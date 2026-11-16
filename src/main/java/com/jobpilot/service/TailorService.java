package com.jobpilot.service;

import com.jobpilot.entity.Job;
import com.jobpilot.model.Profile;
import com.jobpilot.model.TailoredResume;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a resume for one job. It may only SELECT and REORDER what is already in the profile:
 * nothing is invented, and skills the job wants but you lack are reported as gaps, never added.
 */
@Service
public class TailorService {

    private static final Set<String> STOP = Set.of("this", "that", "with", "will", "have", "from", "your", "you", "our", "the",
            "and", "for", "are", "work", "team", "role", "years", "experience", "ability", "skills", "strong", "good", "must");

    private final SkillTaxonomy taxonomy;
    private final MatchService matcher;

    public TailorService(SkillTaxonomy taxonomy, MatchService matcher) {
        this.taxonomy = taxonomy;
        this.matcher = matcher;
    }

    public TailoredResume tailor(Job job, Profile p) {
        String jdText = (job.getTitle() == null ? "" : job.getTitle()) + " " + (job.getDescription() == null ? "" : job.getDescription());
        Set<String> jdSkills = taxonomy.extract(jdText);
        Set<String> mine = matcher.profileSkills(p);
        List<String> matched = jdSkills.stream().filter(mine::contains).collect(Collectors.toList());
        List<String> gaps = jdSkills.stream().filter(s -> !mine.contains(s)).collect(Collectors.toList());
        Set<String> keywords = keywords(jdText);

        // skills: job-relevant ones first, nothing added
        List<String> base = p.getSkills().isEmpty() ? new ArrayList<>(mine) : new ArrayList<>(p.getSkills());
        List<String> skills = new ArrayList<>();
        base.stream().filter(s -> jdSkills.contains(taxonomy.canonical(s))).forEach(skills::add);
        base.stream().filter(s -> !skills.contains(s)).forEach(skills::add);

        // experience: every role stays, bullets ranked by relevance
        List<Profile.Experience> exp = new ArrayList<>();
        String topBullet = null;
        String topBulletRole = null;
        int topScore = -1;
        for (int i = 0; i < p.getExperience().size(); i++) {
            Profile.Experience src = p.getExperience().get(i);
            Profile.Experience copy = new Profile.Experience();
            copy.setCompany(src.getCompany());
            copy.setTitle(src.getTitle());
            copy.setStart(src.getStart());
            copy.setEnd(src.getEnd());
            List<String> ranked = rank(src.getBullets(), jdSkills, keywords);
            copy.setBullets(new ArrayList<>(ranked.subList(0, Math.min(ranked.size(), i < 2 ? 5 : 3))));
            exp.add(copy);
            if (!ranked.isEmpty()) {
                int sc = bulletScore(ranked.get(0), jdSkills, keywords);
                if (sc > topScore) {
                    topScore = sc;
                    topBullet = ranked.get(0);
                    topBulletRole = src.getTitle() + (src.getCompany().isBlank() ? "" : " at " + src.getCompany());
                }
            }
        }

        // projects: most relevant first, max 3
        List<Profile.Project> projects = new ArrayList<>(p.getProjects());
        projects.sort(Comparator.comparingInt((Profile.Project pr) -> -projectScore(pr, jdSkills, keywords)));
        List<Profile.Project> pOut = new ArrayList<>();
        for (Profile.Project src : projects.subList(0, Math.min(3, projects.size()))) {
            Profile.Project c = new Profile.Project();
            c.setName(src.getName());
            c.setTech(src.getTech());
            List<String> ranked = rank(src.getBullets(), jdSkills, keywords);
            c.setBullets(new ArrayList<>(ranked.subList(0, Math.min(3, ranked.size()))));
            pOut.add(c);
        }

        String headline = !p.getHeadline().isBlank() ? p.getHeadline()
                : (!p.getExperience().isEmpty() ? p.getExperience().get(0).getTitle() : "");

        String summary = p.getSummary();
        if (summary == null || summary.isBlank()) {
            List<String> top = (matched.isEmpty() ? skills : matched).stream().limit(5).collect(Collectors.toList());
            String who = headline.isBlank() ? "Software developer" : headline;
            String yrs = p.getYearsExperience() > 0 ? " with " + trim(p.getYearsExperience()) + " years of experience" : "";
            summary = top.isEmpty() ? "" : who + yrs + " working with " + String.join(", ", top) + ".";
        }

        List<String> contact = new ArrayList<>();
        for (String c : new String[]{p.getEmail(), p.getPhone(), p.getLocation(), p.getGithub(), p.getLinkedin()}) {
            if (c != null && !c.isBlank()) contact.add(c);
        }

        String cover = coverLetter(job, p, matched, topBullet, topBulletRole);
        return new TailoredResume(p.getFullName(), String.join("  |  ", contact), headline, summary, skills, exp, pOut,
                p.getEducation(), p.getCertifications(), matched, gaps, cover);
    }

    private String coverLetter(Job job, Profile p, List<String> matched, String bullet, String role) {
        StringBuilder sb = new StringBuilder();
        String company = job.getCompany() == null || job.getCompany().isBlank() ? "the team" : job.getCompany();
        sb.append("Dear Hiring Team at ").append(company).append(",\n\n");
        sb.append("I am writing to apply for the ").append(job.getTitle()).append(" position.");
        if (!matched.isEmpty()) {
            sb.append(" My background includes hands-on work with ")
                    .append(String.join(", ", matched.stream().limit(5).collect(Collectors.toList())))
                    .append(", which lines up with what you are looking for.");
        }
        sb.append("\n\n");
        if (bullet != null) {
            String b = bullet.strip();
            if (!b.endsWith(".")) b += ".";
            sb.append("In my role as ").append(role).append(": ").append(b).append("\n\n");
        }
        sb.append("I would welcome the chance to discuss how I can contribute. Thank you for your time and consideration.\n\n");
        sb.append("Sincerely,\n").append(p.getFullName());
        return sb.toString();
    }

    private List<String> rank(List<String> bullets, Set<String> jdSkills, Set<String> keywords) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < bullets.size(); i++) idx.add(i);
        idx.sort(Comparator.comparingInt((Integer i) -> -bulletScore(bullets.get(i), jdSkills, keywords)).thenComparingInt(i -> i));
        List<String> out = new ArrayList<>();
        for (int i : idx) out.add(bullets.get(i));
        return out;
    }

    private int bulletScore(String bullet, Set<String> jdSkills, Set<String> keywords) {
        int score = 0;
        for (String s : taxonomy.extract(bullet)) if (jdSkills.contains(s)) score += 3;
        for (String w : bullet.toLowerCase().split("[^a-z0-9+#.]+")) if (keywords.contains(w)) score++;
        return score;
    }

    private int projectScore(Profile.Project pr, Set<String> jdSkills, Set<String> keywords) {
        int score = bulletScore(pr.getTech() + " " + pr.getName(), jdSkills, keywords);
        for (String b : pr.getBullets()) score += bulletScore(b, jdSkills, keywords);
        return score;
    }

    private Set<String> keywords(String text) {
        Set<String> out = new HashSet<>();
        for (String w : text.toLowerCase().split("[^a-z0-9+#.]+")) if (w.length() >= 4 && !STOP.contains(w)) out.add(w);
        return out;
    }

    private String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
    }
}
