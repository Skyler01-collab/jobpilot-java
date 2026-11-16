package com.jobpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.entity.Job;
import com.jobpilot.model.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Optional: drafts short answers to application questions with the Claude API.
 * Turned on by setting ANTHROPIC_API_KEY. The profile and job text are sent to Anthropic when it is used.
 */
@Service
public class AiService {

    private static final String SYSTEM = """
            You draft answers to job application questions on behalf of the candidate described in the profile JSON.
            Rules: use only facts that appear in the profile. Never invent employers, dates, numbers, tools, certifications or experience.
            Write in first person, plain text, at most 100 words, no greeting or sign-off.
            If the profile does not contain enough to answer honestly, or the question asks for a number, salary, notice period,
            personal identifier or yes/no eligibility fact, reply with exactly NO_ANSWER.
            """;

    private final RestClient http = RestClient.create();
    private final ObjectMapper mapper;

    @Value("${jobpilot.ai.api-key:}")
    private String apiKey;
    @Value("${jobpilot.ai.model:claude-haiku-5-5}")
    private String model;

    public AiService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Returns a draft answer, or an empty string when the model has nothing honest to say. */
    public String answer(String question, Job job, Profile profile) {
        if (!configured()) throw new IllegalArgumentException("AI drafting is not set up (ANTHROPIC_API_KEY is empty)");
        if (question == null || question.isBlank()) throw new IllegalArgumentException("Question is empty");
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("Profile JSON:\n").append(mapper.writeValueAsString(profile)).append("\n\n");
            if (job != null) {
                String d = job.getDescription() == null ? "" : job.getDescription();
                prompt.append("Job: ").append(job.getTitle()).append(" at ").append(job.getCompany()).append("\n")
                        .append(d.length() > 3500 ? d.substring(0, 3500) : d).append("\n\n");
            }
            prompt.append("Application question: ").append(question);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 400,
                    "system", SYSTEM,
                    "messages", List.of(Map.of("role", "user", "content", prompt.toString())));

            JsonNode res = http.post().uri("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(JsonNode.class);
            String text = res == null ? "" : res.path("content").path(0).path("text").asText("").trim();
            return text.equals("NO_ANSWER") || text.startsWith("NO_ANSWER") ? "" : text;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("AI request failed: " + e.getMessage(), e);
        }
    }
}
