package com.jobpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.entity.ProfileEntity;
import com.jobpilot.model.Profile;
import com.jobpilot.repo.ProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final ProfileRepository repo;
    private final ObjectMapper mapper;

    public ProfileService(ProfileRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public Profile get() {
        return repo.findById(1L).map(e -> {
            try {
                return mapper.readValue(e.getJson(), Profile.class);
            } catch (Exception ex) {
                return new Profile();
            }
        }).orElseGet(Profile::new);
    }

    public Profile save(Profile profile) {
        normalize(profile);
        try {
            ProfileEntity e = repo.findById(1L).orElseGet(ProfileEntity::new);
            e.setId(1L);
            e.setJson(mapper.writeValueAsString(profile));
            repo.save(e);
            return profile;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not save profile: " + ex.getMessage(), ex);
        }
    }

    private void normalize(Profile p) {
        if (p.getSkills() == null) p.setSkills(new java.util.ArrayList<>());
        if (p.getTargetTitles() == null) p.setTargetTitles(new java.util.ArrayList<>());
        if (p.getPreferredLocations() == null) p.setPreferredLocations(new java.util.ArrayList<>());
        if (p.getExcludedKeywords() == null) p.setExcludedKeywords(new java.util.ArrayList<>());
        if (p.getCertifications() == null) p.setCertifications(new java.util.ArrayList<>());
        if (p.getExperience() == null) p.setExperience(new java.util.ArrayList<>());
        if (p.getEducation() == null) p.setEducation(new java.util.ArrayList<>());
        if (p.getProjects() == null) p.setProjects(new java.util.ArrayList<>());
        for (Profile.Experience e : p.getExperience()) if (e.getBullets() == null) e.setBullets(new java.util.ArrayList<>());
        for (Profile.Project pr : p.getProjects()) if (pr.getBullets() == null) pr.setBullets(new java.util.ArrayList<>());
    }
}
