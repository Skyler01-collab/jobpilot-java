package com.jobpilot.web;

import com.jobpilot.entity.Job;
import com.jobpilot.service.AiService;
import com.jobpilot.service.JobService;
import com.jobpilot.service.ProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    public record AnswerRequest(String question, Long jobId) {}

    private final AiService ai;
    private final JobService jobs;
    private final ProfileService profiles;

    public AiController(AiService ai, JobService jobs, ProfileService profiles) {
        this.ai = ai;
        this.jobs = jobs;
        this.profiles = profiles;
    }

    @PostMapping("/answer")
    public Map<String, String> answer(@RequestBody AnswerRequest r) {
        Job job = r.jobId() == null ? null : jobs.get(r.jobId());
        return Map.of("answer", ai.answer(r.question(), job, profiles.get()));
    }
}
