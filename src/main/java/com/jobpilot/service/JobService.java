package com.jobpilot.service;

import com.jobpilot.entity.Job;
import com.jobpilot.model.Profile;
import com.jobpilot.repo.JobRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
@Service
public class JobService {

    public record FetchResult(int added, int skipped, List<String> errors) {}

    private final JobRepository repo;
    private final ProfileService profiles;
    private final MatchService matcher;
    private final JobSourceClient sources;

    public JobService(JobRepository repo, ProfileService profiles, MatchService matcher, JobSourceClient sources) {
        this.repo = repo;
        this.profiles = profiles;
        this.matcher = matcher;
        this.sources = sources;
    }

    public List<Job> list() {
        return repo.findAllByOrderByScoreDescCreatedAtDesc();
    }

    public Job get(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
    }

    /** Adds a manually entered or extension-captured job. Returns the existing one if the URL was seen before. */
    public Job addManual(String title, String company, String location, String url, String description, Boolean remote) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required");
        String ext = (url != null && !url.isBlank()) ? "url:" + url : null;
        if (ext != null) {
            Optional<Job> existing = repo.findByExternalId(ext);
            if (existing.isPresent()) return existing.get();
        }
        Job j = new Job();
        j.setExternalId(ext);
        j.setSource("Manual");
        j.setTitle(title.strip());
        j.setCompany(company == null ? "" : company.strip());
        j.setLocation(location == null ? "" : location.strip());
        j.setUrl(url);
        j.setDescription(description == null ? "" : description);
        boolean isRemote = remote != null ? remote : (safe(location) + " " + safe(description)).toLowerCase().contains("remote");
        j.setRemote(isRemote);
        applyScore(j, profiles.get());
        return repo.save(j);
    }

    /** Used by the browser extension: creates or enriches a job seen on Naukri/Indeed. */
    public synchronized Job capture(String source, String key, String title, String company, String location, String url, String description) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Key is required");
        String src = (source == null || source.isBlank()) ? "Extension" : source.strip();
        String ext = trunc(src.toLowerCase() + ":" + key.strip(), 590);
        Profile p = profiles.get();
        String d = description == null ? "" : description;

        Optional<Job> existing = repo.findByExternalId(ext);
        if (existing.isPresent()) {
            Job j = existing.get();
            boolean changed = false;
            if (d.length() > safe(j.getDescription()).length() + 40) { j.setDescription(d); changed = true; }
            if (safe(j.getCompany()).isBlank() && company != null && !company.isBlank()) { j.setCompany(trunc(company.strip(), 250)); changed = true; }
            if (safe(j.getLocation()).isBlank() && location != null && !location.isBlank()) { j.setLocation(trunc(location.strip(), 250)); changed = true; }
            if (changed) { applyScore(j, p); repo.save(j); }
            return j;
        }

        Job j = new Job();
        j.setExternalId(ext);
        j.setSource(trunc(src, 250));
        j.setTitle(trunc(title.strip(), 250));
        j.setCompany(company == null ? "" : trunc(company.strip(), 250));
        j.setLocation(location == null ? "" : trunc(location.strip(), 250));
        j.setUrl(trunc(url, 1000));
        j.setDescription(d);
        String hay = (safe(location) + " " + title + " " + d).toLowerCase();
        j.setRemote(hay.contains("remote") || hay.contains("work from home"));
        applyScore(j, p);
        try {
            return repo.save(j);
        } catch (DataIntegrityViolationException dup) {
            // another request saved the same job a moment ago: use that one
            return repo.findByExternalId(ext).orElseThrow(() -> dup);
        }
    }

    private static String trunc(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    public FetchResult fetch(String query, String location) {
        Profile p = profiles.get();
        List<String> queries = new ArrayList<>();
        if (query != null && !query.isBlank()) queries.add(query.strip());
        else for (String t : p.getTargetTitles()) if (!t.isBlank() && queries.size() < 3) queries.add(t);
        if (queries.isEmpty()) queries.add("java developer");

        String where = (location != null && !location.isBlank()) ? location : p.getLocation();
        int added = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        for (String q : queries) {
            List<Job> found = new ArrayList<>();
            try {
                found.addAll(sources.remotive(q));
            } catch (Exception e) {
                errors.add("Remotive: " + e.getMessage());
            }
            if (sources.adzunaConfigured()) {
                try {
                    found.addAll(sources.adzuna(q, where));
                } catch (Exception e) {
                    errors.add("Adzuna: " + e.getMessage());
                }
            }
            for (Job j : found) {
                if (repo.existsByExternalId(j.getExternalId())) { skipped++; continue; }
                applyScore(j, p);
                repo.save(j);
                added++;
            }
        }
        return new FetchResult(added, skipped, errors);
    }

    public int rescoreAll() {
        Profile p = profiles.get();
        List<Job> all = repo.findAll();
        for (Job j : all) applyScore(j, p);
        repo.saveAll(all);
        return all.size();
    }

    public Job setStatus(Long id, String status) {
        Job j = get(id);
        try {
            j.setStatus(Job.Status.valueOf(status.toUpperCase()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown status: " + status);
        }
        return repo.save(j);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    private void applyScore(Job j, Profile p) {
        MatchService.MatchResult r = matcher.score(j, p);
        j.setScore(r.score());
        j.setMatchedSkills(String.join(", ", r.matched()));
        j.setMissingSkills(String.join(", ", r.missing()));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
