package com.jobpilot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** The one detailed profile the whole app works from. Stored as JSON. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Profile {
    private String fullName = "";
    private String email = "";
    private String phone = "";
    private String location = "";
    private String github = "";
    private String linkedin = "";
    private String headline = "";
    private String summary = "";
    private double yearsExperience = 0;
    private boolean remoteOk = true;

    private List<String> skills = new ArrayList<>();
    private List<String> targetTitles = new ArrayList<>();
    private List<String> preferredLocations = new ArrayList<>();
    private List<String> excludedKeywords = new ArrayList<>();
    private List<String> certifications = new ArrayList<>();

    private List<Experience> experience = new ArrayList<>();
    private List<Education> education = new ArrayList<>();
    private List<Project> projects = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Experience {
        private String company = "";
        private String title = "";
        private String start = "";
        private String end = "";
        private List<String> bullets = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Education {
        private String school = "";
        private String degree = "";
        private String year = "";
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Project {
        private String name = "";
        private String tech = "";
        private List<String> bullets = new ArrayList<>();
    }
}
