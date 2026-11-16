package com.jobpilot.web;

import com.jobpilot.entity.Job;
import com.jobpilot.service.JobService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/** Endpoints used by the Chrome extension. */
@RestController
@RequestMapping("/api/ext")
public class ExtController {

    public record CaptureItem(String key, String source, String title, String company, String location, String url, String description) {}

    public record CaptureRequest(List<CaptureItem> jobs) {}

    public record CaptureResult(String key, Long id, int score, String status, String matched, String missing) {}

    private final JobService jobs;

    public ExtController(JobService jobs) {
        this.jobs = jobs;
    }

    /** Saves jobs seen on Naukri/Indeed (list cards or full pages) and returns their match scores. */
    @PostMapping("/capture")
    public List<CaptureResult> capture(@RequestBody CaptureRequest r) {
        List<CaptureResult> out = new ArrayList<>();
        if (r == null || r.jobs() == null) return out;
        for (CaptureItem i : r.jobs().subList(0, Math.min(100, r.jobs().size()))) {
            try {
                Job j = jobs.capture(i.source(), i.key(), i.title(), i.company(), i.location(), i.url(), i.description());
                out.add(new CaptureResult(i.key(), j.getId(), j.getScore(), j.getStatus().name(), j.getMatchedSkills(), j.getMissingSkills()));
            } catch (RuntimeException skipped){
                // skip items that are missing a title or key
            }
        }
        return out;
    }
}
