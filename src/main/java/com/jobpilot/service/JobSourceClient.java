package com.jobpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.entity.Job;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/** Pulls postings from public job APIs: Remotive (no key) and Adzuna (free key). */
@Component
public class JobSourceClient {

    private final RestClient http = RestClient.builder().defaultHeader("User-Agent", "JobPilot/1.0").build();

    @Value("${jobpilot.adzuna.app-id:}")
    private String adzunaId;
    @Value("${jobpilot.adzuna.app-key:}")
    private String adzunaKey;
    @Value("${jobpilot.adzuna.country:in}")
    private String adzunaCountry;

    public boolean adzunaConfigured() {
        return adzunaId != null && !adzunaId.isBlank() && adzunaKey != null && !adzunaKey.isBlank();
    }

    public List<Job> remotive(String query) {
        var uri = UriComponentsBuilder.fromUriString("https://remotive.com/api/remote-jobs")
                .queryParam("search", query).queryParam("limit", 30).build().encode().toUri();
        JsonNode root = http.get().uri(uri).retrieve().body(JsonNode.class);
        List<Job> out = new ArrayList<>();
        if (root == null) return out;
        for (JsonNode n : root.path("jobs")) {
            Job j = new Job();
            j.setExternalId("remotive:" + n.path("id").asText());
            j.setSource("Remotive");
            j.setTitle(n.path("title").asText());
            j.setCompany(n.path("company_name").asText());
            j.setLocation(n.path("candidate_required_location").asText("Remote"));
            j.setUrl(n.path("url").asText());
            j.setDescription(stripHtml(n.path("description").asText()));
            j.setRemote(true);
            out.add(j);
        }
        return out;
    }

    public List<Job> adzuna(String query, String where) {
        var b = UriComponentsBuilder.fromUriString("https://api.adzuna.com/v1/api/jobs/" + adzunaCountry + "/search/1")
                .queryParam("app_id", adzunaId).queryParam("app_key", adzunaKey)
                .queryParam("what", query).queryParam("results_per_page", 30);
        if (where != null && !where.isBlank()) b.queryParam("where", where);
        JsonNode root = http.get().uri(b.build().encode().toUri()).retrieve().body(JsonNode.class);
        List<Job> out = new ArrayList<>();
        if (root == null) return out;
        for (JsonNode n : root.path("results")) {
            Job j = new Job();
            j.setExternalId("adzuna:" + n.path("id").asText());
            j.setSource("Adzuna");
            j.setTitle(stripHtml(n.path("title").asText()));
            j.setCompany(n.path("company").path("display_name").asText());
            j.setLocation(n.path("location").path("display_name").asText());
            j.setUrl(n.path("redirect_url").asText());
            j.setDescription(stripHtml(n.path("description").asText()));
            j.setRemote(j.getDescription().toLowerCase().contains("remote"));
            out.add(j);
        }
        return out;
    }

    static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("(?i)<br\\s*/?>|</p>|</li>", "\n").replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n").strip();
    }
}
