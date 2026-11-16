package com.jobpilot.web;

import com.jobpilot.entity.Job;
import com.jobpilot.model.TailoredResume;
import com.jobpilot.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    public record JobRequest(String title, String company, String location, String url, String description, Boolean remote) {}

    public record FetchRequest(String query, String location) {}

    /** What the jobs table needs; the (large) description is only sent by the detail endpoint. */
    public record JobView(Long id, String title, String company, String location, String source, String url, boolean remote,
                          int score, String matchedSkills, String missingSkills, String status, Instant createdAt,
                          String description) {
        static JobView of(Job j, boolean withDescription) {
            return new JobView(j.getId(), j.getTitle(), j.getCompany(), j.getLocation(), j.getSource(), j.getUrl(), j.isRemote(),
                    j.getScore(), j.getMatchedSkills(), j.getMissingSkills(), j.getStatus().name(), j.getCreatedAt(),
                    withDescription ? j.getDescription() : null);
        }
    }

    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final JobService jobs;
    private final ProfileService profiles;
    private final TailorService tailor;
    private final ResumeRenderer renderer;

    @Value("${jobpilot.data-dir:./data}")
    private String dataDir;

    public JobController(JobService jobs, ProfileService profiles, TailorService tailor, ResumeRenderer renderer) {
        this.jobs = jobs;
        this.profiles = profiles;
        this.tailor = tailor;
        this.renderer = renderer;
    }

    @GetMapping
    public List<JobView> list() {
        return jobs.list().stream().map(j -> JobView.of(j, false)).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public JobView one(@PathVariable Long id) {
        return JobView.of(jobs.get(id), true);
    }

    @PostMapping
    public JobView add(@RequestBody JobRequest r) {
        return JobView.of(jobs.addManual(r.title(), r.company(), r.location(), r.url(), r.description(), r.remote()), false);
    }

    @PostMapping("/fetch")
    public JobService.FetchResult fetch(@RequestBody(required = false) FetchRequest r) {
        return jobs.fetch(r == null ? null : r.query(), r == null ? null : r.location());
    }

    @PostMapping("/rescore")
    public Map<String, Integer> rescore() {
        return Map.of("rescored", jobs.rescoreAll());
    }

    @PatchMapping("/{id}/status")
    public JobView status(@PathVariable Long id, @RequestParam String value) {
        return JobView.of(jobs.setStatus(id, value), false);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable Long id) {
        jobs.delete(id);
        return Map.of("deleted", true);
    }

    /** Builds the tailored resume + cover letter, saves copies under data/resumes/<jobId>/, and queues the job. */
    @PostMapping("/{id}/tailor")
    public TailoredResume tailorResume(@PathVariable Long id) throws IOException {
        Job job = jobs.get(id);
        TailoredResume r = tailor.tailor(job, profiles.get());
        Path dir = Paths.get(dataDir, "resumes", String.valueOf(id));
        Files.createDirectories(dir);
        Files.write(dir.resolve("resume.pdf"), renderer.pdf(r));
        try {
            Files.write(dir.resolve("resume.docx"), renderer.docx(r));
        } catch (Exception | LinkageError e) {
            System.err.println("Word resume could not be created (the PDF still works): " + e);
        }
        Files.writeString(dir.resolve("cover-letter.txt"), r.coverLetter(), StandardCharsets.UTF_8);
        if (job.getStatus() == Job.Status.NEW) jobs.setStatus(id, "QUEUED");
        return r;
    }

    @GetMapping("/{id}/resume.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Job job = jobs.get(id);
        byte[] bytes = renderer.pdf(tailor.tailor(job, profiles.get()));
        return download(bytes, MediaType.APPLICATION_PDF, fileName(job, "pdf"));
    }

    @GetMapping("/{id}/resume.docx")
    public ResponseEntity<byte[]> docx(@PathVariable Long id) {
        Job job = jobs.get(id);
        byte[] bytes = renderer.docx(tailor.tailor(job, profiles.get()));
        return download(bytes, DOCX, fileName(job, "docx"));
    }

    @GetMapping("/{id}/cover-letter.txt")
    public ResponseEntity<byte[]> cover(@PathVariable Long id) {
        Job job = jobs.get(id);
        byte[] bytes = tailor.tailor(job, profiles.get()).coverLetter().getBytes(StandardCharsets.UTF_8);
        return download(bytes, MediaType.TEXT_PLAIN, fileName(job, "txt").replace("Resume", "CoverLetter"));
    }

    private ResponseEntity<byte[]> download(byte[] bytes, MediaType type, String name) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(type)
                .body(bytes);
    }

    private String fileName(Job job, String ext) {
        String who = profiles.get().getFullName();
        String base = (who == null || who.isBlank() ? "Resume" : who) + "_" + (job.getCompany() == null ? "" : job.getCompany()) + "_Resume";
        return base.replaceAll("[^A-Za-z0-9_-]+", "_") + "." + ext;
    }
}
