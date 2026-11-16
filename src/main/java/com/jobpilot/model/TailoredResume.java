package com.jobpilot.model;

import java.util.List;

/** A resume built for one specific job. Only reorders/selects what is in the profile. */
public record TailoredResume(
        String fullName,
        String contactLine,
        String headline,
        String summary,
        List<String> skills,
        List<Profile.Experience> experience,
        List<Profile.Project> projects,
        List<Profile.Education> education,
        List<String> certifications,
        List<String> matchedSkills,
        List<String> gapSkills,
        String coverLetter) {
}
