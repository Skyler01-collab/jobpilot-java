package com.jobpilot.service;

import com.jobpilot.entity.Job;
import com.jobpilot.model.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scores a job against the profile, 0-100:
 * skills 55, title 20, experience 15, location 10.
 */
@Service
public class MatchService {

    public record MatchResult(int score, List<String> matched, List<String> missing) {}

    private static final Set<String> STOP = Set.of("and", "the", "for", "with", "of", "a", "an", "in", "at", "to", "ii", "i",
            "iii", "sr", "jr", "senior", "junior", "lead", "associate", "remote");
    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\s*(?:\\+|-\\s*\\d{1,2})?\\s*(?:years|yrs|year)", Pattern.CASE_INSENSITIVE);

    private final SkillTaxonomy taxonomy;

    public MatchService(SkillTaxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    /** Every skill the profile can actually back up: listed skills plus those mentioned in roles/projects. */
    public Set<String> profileSkills(Profile p) {
        StringBuilder sb = new StringBuilder(String.join(" , ", p.getSkills())).append(' ');
        for (Profile.Experience e : p.getExperience()) sb.append(String.join(" ", e.getBullets())).append(' ');
        for (Profile.Project pr : p.getProjects()) sb.append(pr.getTech()).append(' ').append(String.join(" ", pr.getBullets())).append(' ');
        sb.append(p.getSummary());
        return taxonomy.extract(sb.toString());
    }

    public MatchResult score(Job job, Profile p) {
        String text = safe(job.getTitle()) + " " + safe(job.getDescription());
        String lower = (text + " " + safe(job.getCompany())).toLowerCase();

        for (String bad : p.getExcludedKeywords()) {
            if (!bad.isBlank() && lower.contains(bad.trim().toLowerCase())) {
                return new MatchResult(0, List.of(), List.of());
            }
        }

        Set<String> jd = taxonomy.extract(text);
        Set<String> mine = profileSkills(p);
        List<String> matched = jd.stream().filter(mine::contains).collect(Collectors.toList());
        List<String> missing = jd.stream().filter(s -> !mine.contains(s)).collect(Collectors.toList());

        // skills: neutral when the JD lists very few skills
        double ratio = jd.isEmpty() ? 0.5 : (double) matched.size() / jd.size();
        double weight = Math.min(1.0, jd.size() / 5.0);
        double skills = 0.5 * (1 - weight) + ratio * weight;

        double total = 55 * skills + 20 * titleScore(job.getTitle(), p)
                + 15 * experienceScore(text, p) + 10 * locationScore(job, p);
        return new MatchResult((int) Math.round(Math.max(0, Math.min(100, total))), matched, missing);
    }

    private double titleScore(String jobTitle, Profile p) {
        Set<String> job = tokens(jobTitle);
        List<String> mine = new ArrayList<>(p.getTargetTitles());
        for (Profile.Experience e : p.getExperience()) mine.add(e.getTitle());
        if (job.isEmpty() || mine.stream().allMatch(String::isBlank)) return 0.5;
        double best = 0;
        for (String t : mine) {
            Set<String> a = tokens(t);
            if (a.isEmpty()) continue;
            Set<String> inter = new HashSet<>(a);
            inter.retainAll(job);
            best = Math.max(best, (double) inter.size() / Math.min(a.size(), job.size()));
        }
        return best;
    }

    private double experienceScore(String text, Profile p) {
        Matcher m = YEARS.matcher(text);
        if (!m.find()) return 0.8;
        int required = Integer.parseInt(m.group(1));
        if (required <= 0 || required > 30) return 0.8;
        double have = p.getYearsExperience();
        if (have >= required) return 1.0;
        return Math.max(0, 1.0 - (required - have) / required);
    }

    private double locationScore(Job job, Profile p) {
        if (job.isRemote() && p.isRemoteOk()) return 1.0;
        String loc = safe(job.getLocation()).toLowerCase();
        if (loc.isBlank()) return 0.5;
        List<String> prefs = new ArrayList<>(p.getPreferredLocations());
        prefs.add(p.getLocation());
        for (String pref : prefs) {
            if (!pref.isBlank() && loc.contains(pref.trim().toLowerCase())) return 1.0;
        }
        return 0.2;
    }

    private Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        for (String w : safe(s).toLowerCase().split("[^a-z0-9+#.]+")) {
            if (w.length() > 1 && !STOP.contains(w)) out.add(w);
        }
        return out;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
