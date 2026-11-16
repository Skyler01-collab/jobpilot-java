package com.jobpilot.service;

import com.jobpilot.model.Profile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a PDF/DOCX/TXT resume and fills a Profile using simple heuristics. Always review the result. */
@Service
public class ResumeImportService {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s().-]{8,}\\d)");
    private static final Pattern GITHUB = Pattern.compile("github\\.com/[A-Za-z0-9_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINKEDIN = Pattern.compile("linkedin\\.com/in/[A-Za-z0-9_%-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile(
            "^(professional |work |technical |key )?(experience|work experience|employment( history)?|education|academics|projects?|"
                    + "personal projects?|skills|technical skills|core competencies|certifications?|summary|professional summary|profile|objective|achievements)\\s*:?$",
            Pattern.CASE_INSENSITIVE);
    private static final String MONTH = "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?";
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?i)((?:" + MONTH + "\\s+)?\\d{4})\\s*(?:-|–|—|to)\\s*((?:" + MONTH + "\\s+)?\\d{4}|present|current|till date|now)");
    private static final Pattern BULLET = Pattern.compile("^\\s*[•\\-*▪●◦–·]\\s*");
    private static final Pattern DEGREE = Pattern.compile("(?i)b\\.?tech|m\\.?tech|bachelor|master|mba|b\\.?sc|m\\.?sc|bca|mca|diploma|b\\.?e\\b|12th|10th|intermediate");
    private static final Pattern SCHOOL = Pattern.compile("(?i)university|college|institute|school|academy|technolog|polytechnic");

    private final SkillTaxonomy taxonomy;

    public ResumeImportService(SkillTaxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    public String extractText(MultipartFile file) {
        try {
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
            byte[] bytes = file.getBytes();
            if (name.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    return new PDFTextStripper().getText(doc);
                }
            }
            if (name.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                     XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                    return ex.getText();
                }
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read that file: " + e.getMessage());
        }
    }

    /** Merge parsed data into an existing profile: only fills blanks and adds new skills. */
    public Profile merge(Profile existing, Profile parsed) {
        if (blank(existing.getFullName())) existing.setFullName(parsed.getFullName());
        if (blank(existing.getEmail())) existing.setEmail(parsed.getEmail());
        if (blank(existing.getPhone())) existing.setPhone(parsed.getPhone());
        if (blank(existing.getGithub())) existing.setGithub(parsed.getGithub());
        if (blank(existing.getLinkedin())) existing.setLinkedin(parsed.getLinkedin());
        if (blank(existing.getHeadline())) existing.setHeadline(parsed.getHeadline());
        if (blank(existing.getSummary())) existing.setSummary(parsed.getSummary());
        if (existing.getYearsExperience() == 0) existing.setYearsExperience(parsed.getYearsExperience());

        Map<String, String> skills = new LinkedHashMap<>();
        for (String s : existing.getSkills()) skills.put(s.toLowerCase(), s);
        for (String s : parsed.getSkills()) skills.putIfAbsent(s.toLowerCase(), s);
        existing.setSkills(new ArrayList<>(skills.values()));

        if (existing.getTargetTitles().isEmpty()) existing.setTargetTitles(parsed.getTargetTitles());
        if (existing.getExperience().isEmpty()) existing.setExperience(parsed.getExperience());
        if (existing.getEducation().isEmpty()) existing.setEducation(parsed.getEducation());
        if (existing.getProjects().isEmpty()) existing.setProjects(parsed.getProjects());
        if (existing.getCertifications().isEmpty()) existing.setCertifications(parsed.getCertifications());
        return existing;
    }

    public Profile parse(String text) {
        Profile p = new Profile();
        if (text == null || text.isBlank()) return p;

        List<String> lines = new ArrayList<>();
        for (String l : text.split("\\R")) {
            String t = l.strip();
            if (!t.isEmpty()) lines.add(t);
        }

        // contact details
        Matcher m = EMAIL.matcher(text);
        if (m.find()) p.setEmail(m.group());
        m = PHONE.matcher(text);
        while (m.find()) {
            int digits = m.group().replaceAll("\\D", "").length();
            if (digits >= 10 && digits <= 13 && !DATE_RANGE.matcher(m.group()).find()) {
                p.setPhone(m.group().strip());
                break;
            }
        }
        m = GITHUB.matcher(text);
        if (m.find()) p.setGithub("https://" + m.group());
        m = LINKEDIN.matcher(text);
        if (m.find()) p.setLinkedin("https://www." + m.group());
        if (!lines.isEmpty()) {
            String first = lines.get(0);
            if (first.split("\\s+").length <= 5 && !first.matches(".*[\\d@].*") && !HEADING.matcher(first).matches()) {
                p.setFullName(first);
            }
        }

        // split into sections
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = "header";
        sections.put(current, new ArrayList<>());
        for (String line : lines) {
            if (HEADING.matcher(line).matches()) {
                current = sectionKey(line);
                sections.computeIfAbsent(current, k -> new ArrayList<>());
            } else {
                sections.get(current).add(line);
            }
        }

        // skills
        Map<String, String> skills = new LinkedHashMap<>();
        for (String s : taxonomy.extract(text)) skills.put(s.toLowerCase(), s);
        for (String line : sections.getOrDefault("skills", List.of())) {
            String cleaned = line.contains(":") ? line.substring(line.indexOf(':') + 1) : line;
            for (String tok : cleaned.split("[,|•;]")) {
                String t = tok.strip();
                if (t.length() >= 2 && t.length() <= 30) skills.putIfAbsent(t.toLowerCase(), taxonomy.canonical(t));
            }
        }
        p.setSkills(new ArrayList<>(skills.values()));

        p.setSummary(String.join(" ", sections.getOrDefault("summary", List.of())));
        p.setExperience(parseExperience(sections.getOrDefault("experience", List.of())));
        p.setEducation(parseEducation(sections.getOrDefault("education", List.of())));
        p.setProjects(parseProjects(sections.getOrDefault("projects", List.of())));

        List<String> certs = new ArrayList<>();
        for (String l : sections.getOrDefault("certifications", List.of())) certs.add(BULLET.matcher(l).replaceFirst(""));
        p.setCertifications(certs);

        // derived hints
        if (!p.getExperience().isEmpty()) {
            String latest = p.getExperience().get(0).getTitle();
            p.setHeadline(latest);
            if (!blank(latest)) p.setTargetTitles(new ArrayList<>(List.of(latest)));
        }
        p.setYearsExperience(estimateYears(text));
        return p;
    }

    private String sectionKey(String heading) {
        String h = heading.toLowerCase().replace(":", "").strip();
        if (h.contains("experience") || h.contains("employment")) return "experience";
        if (h.contains("education") || h.contains("academic")) return "education";
        if (h.contains("project")) return "projects";
        if (h.contains("skill") || h.contains("competenc")) return "skills";
        if (h.contains("certif")) return "certifications";
        return "summary"; // summary, profile, objective, achievements
    }

    private List<Profile.Experience> parseExperience(List<String> lines) {
        List<Profile.Experience> out = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        Profile.Experience cur = null;
        for (String line : lines) {
            Matcher dm = DATE_RANGE.matcher(line);
            if (dm.find()) {
                cur = new Profile.Experience();
                cur.setStart(dm.group(1).strip());
                cur.setEnd(dm.group(2).strip());
                String head = (line.substring(0, dm.start()) + " " + line.substring(dm.end())).replaceAll("[|,(): -]+$", "").strip();
                List<String> parts = new ArrayList<>();
                if (!pending.isEmpty()) {
                    int from = Math.max(0, pending.size() - 2);
                    parts.addAll(pending.subList(from, pending.size()));
                }
                if (!head.isBlank()) parts.add(head);
                assignTitleCompany(cur, parts);
                pending.clear();
                out.add(cur);
            } else if (BULLET.matcher(line).find()) {
                if (cur != null) cur.getBullets().add(BULLET.matcher(line).replaceFirst(""));
            } else if (cur != null && !cur.getBullets().isEmpty() && Character.isLowerCase(line.charAt(0))) {
                int i = cur.getBullets().size() - 1; // wrapped line of the previous bullet
                cur.getBullets().set(i, cur.getBullets().get(i) + " " + line);
            } else if (cur != null && cur.getBullets().isEmpty() && line.length() > 60) {
                cur.getBullets().add(line); // paragraph-style description
            } else {
                pending.add(line);
            }
        }
        return out;
    }

    private void assignTitleCompany(Profile.Experience e, List<String> parts) {
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            for (String t : part.split("\\s+[|@–—-]\\s+|\\s+at\\s+|,\\s+")) {
                if (!t.isBlank()) tokens.add(t.strip());
            }
        }
        if (tokens.isEmpty()) return;
        e.setTitle(tokens.get(0));
        if (tokens.size() > 1) e.setCompany(tokens.get(1));
    }

    private List<Profile.Education> parseEducation(List<String> lines) {
        List<Profile.Education> out = new ArrayList<>();
        Profile.Education cur = null;
        Pattern year = Pattern.compile("(19|20)\\d{2}(\\s*[-–]\\s*(19|20)?\\d{2,4})?");
        for (String line : lines) {
            boolean school = SCHOOL.matcher(line).find();
            boolean degree = DEGREE.matcher(line).find();
            Matcher ym = year.matcher(line);
            if (!school && !degree && !ym.find()) continue;
            if (cur == null || (school && !cur.getSchool().isBlank() && (degree || !cur.getDegree().isBlank()))
                    || (degree && !cur.getDegree().isBlank())) {
                cur = new Profile.Education();
                out.add(cur);
            }
            String clean = year.matcher(line).replaceAll("").replaceAll("[|,()\\s-]+$", "").strip();
            if (school && cur.getSchool().isBlank()) cur.setSchool(clean);
            else if (degree && cur.getDegree().isBlank()) cur.setDegree(clean);
            Matcher y2 = year.matcher(line);
            if (y2.find() && cur.getYear().isBlank()) cur.setYear(y2.group());
        }
        return out;
    }

    private List<Profile.Project> parseProjects(List<String> lines) {
        List<Profile.Project> out = new ArrayList<>();
        Profile.Project cur = null;
        for (String line : lines) {
            if (BULLET.matcher(line).find()) {
                if (cur == null) { cur = new Profile.Project(); cur.setName("Project"); out.add(cur); }
                cur.getBullets().add(BULLET.matcher(line).replaceFirst(""));
            } else if (cur == null || !cur.getBullets().isEmpty()) {
                cur = new Profile.Project();
                String[] split = line.split("\\s+[|–—-]\\s+", 2);
                cur.setName(split[0].strip());
                if (split.length > 1) cur.setTech(split[1].strip());
                out.add(cur);
            } else if (line.toLowerCase().startsWith("tech")) {
                cur.setTech(line.replaceFirst("(?i)^tech[a-z ]*:?\\s*", ""));
            } else {
                cur.getBullets().add(line);
            }
        }
        return out;
    }

    private double estimateYears(String text) {
        int months = 0;
        Matcher m = DATE_RANGE.matcher(text);
        LocalDate now = LocalDate.now();
        while (m.find()) {
            int[] s = monthYear(m.group(1), now);
            int[] e = monthYear(m.group(2), now);
            if (s == null || e == null) continue;
            int diff = (e[1] * 12 + e[0]) - (s[1] * 12 + s[0]);
            if (diff > 0 && diff < 600) months += diff;
        }
        return Math.round(months / 12.0 * 10) / 10.0;
    }

    private int[] monthYear(String s, LocalDate now) {
        String t = s.toLowerCase().strip();
        if (t.matches("present|current|till date|now")) return new int[]{now.getMonthValue(), now.getYear()};
        Matcher y = Pattern.compile("(\\d{4})").matcher(t);
        if (!y.find()) return null;
        int year = Integer.parseInt(y.group(1));
        String[] names = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        int month = 1;
        for (int i = 0; i < names.length; i++) if (t.startsWith(names[i])) month = i + 1;
        return new int[]{month, year};
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
