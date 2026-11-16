package com.jobpilot.web;

import com.jobpilot.model.Profile;
import com.jobpilot.service.JobService;
import com.jobpilot.service.ProfileService;
import com.jobpilot.service.ResumeImportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;
    private final ResumeImportService importer;
    private final JobService jobs;

    public ProfileController(ProfileService profiles, ResumeImportService importer, JobService jobs) {
        this.profiles = profiles;
        this.importer = importer;
        this.jobs = jobs;
    }

    @GetMapping
    public Profile get() {
        return profiles.get();
    }

    @PutMapping
    public Profile save(@RequestBody Profile profile) {
        Profile saved = profiles.save(profile);
        jobs.rescoreAll(); // keep match scores in sync with the profile
        return saved;
    }

    /** Upload a PDF/DOCX/TXT resume. mode=merge fills blanks only; mode=replace overwrites everything. */
    @PostMapping("/import")
    public Profile importFile(@RequestParam("file") MultipartFile file,
                              @RequestParam(defaultValue = "merge") String mode) {
        if (file.isEmpty()) throw new IllegalArgumentException("The file is empty");
        String text = importer.extractText(file);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No text could be read from that file. If it is a scanned PDF, paste the text instead.");
        }
        return apply(text, mode);
    }

    @PostMapping("/import-text")
    public Profile importText(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        if (text.isBlank()) throw new IllegalArgumentException("Paste your resume text first");
        return apply(text, body.getOrDefault("mode", "merge"));
    }

    private Profile apply(String text, String mode) {
        Profile parsed = importer.parse(text);
        Profile result = "replace".equalsIgnoreCase(mode) ? parsed : importer.merge(profiles.get(), parsed);
        Profile saved = profiles.save(result);
        jobs.rescoreAll();
        return saved;
    }
}
