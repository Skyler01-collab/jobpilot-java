package com.jobpilot.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/** A small skill dictionary. Finds known skills (and their aliases) in free text. */
@Component
public class SkillTaxonomy {

    private final Map<String, Pattern> patterns = new LinkedHashMap<>();

    public SkillTaxonomy() {
        add("Java");
        add("Spring Boot", "springboot");
        add("Spring", "spring framework", "spring mvc", "spring core");
        add("Spring Security");
        add("Spring Data", "spring data jpa");
        add("Spring Cloud");
        add("JPA");
        add("Hibernate");
        add("Microservices", "microservice", "micro-services");
        add("REST API", "restful", "rest apis", "restful apis", "rest services");
        add("SQL");
        add("Oracle", "oracle db", "pl/sql", "plsql");
        add("MySQL");
        add("PostgreSQL", "postgres");
        add("MongoDB");
        add("Redis");
        add("Kafka", "apache kafka");
        add("RabbitMQ");
        add("Docker");
        add("Kubernetes", "k8s");
        add("AWS", "amazon web services");
        add("Azure");
        add("GCP", "google cloud");
        add("Git");
        add("Maven");
        add("Gradle");
        add("Jenkins");
        add("CI/CD", "cicd", "ci cd");
        add("JUnit");
        add("Mockito");
        add("Angular");
        add("React", "reactjs", "react.js");
        add("JavaScript");
        add("TypeScript");
        add("HTML");
        add("CSS");
        add("Node.js", "nodejs");
        add("Python");
        add("Kotlin");
        add("C++");
        add("C#");
        add(".NET", "dotnet");
        add("JWT");
        add("OAuth", "oauth2");
        add("Swagger", "openapi");
        add("Agile");
        add("Scrum");
        add("Linux");
        add("DSA", "data structures", "algorithms");
        add("Multithreading", "concurrency", "multi-threading");
        add("Design Patterns");
        add("Microservice Architecture");
        add("Lombok");
        add("H2");
        add("Postman");
        add("Insomnia");
        add("Jira");
        add("Cloud Computing", "cloud");
        add("Virtualization");
        add("Servlet", "servlets");
        add("JSP");
        add("Selenium");
        add("Terraform");
    }

    private void add(String name, String... aliases) {
        List<String> all = new ArrayList<>();
        all.add(name);
        all.addAll(Arrays.asList(aliases));
        StringBuilder alt = new StringBuilder();
        for (String a : all) {
            if (alt.length() > 0) alt.append('|');
            alt.append(Pattern.quote(a.toLowerCase()));
        }
        // not preceded/followed by letters, digits, + or # so "Java" does not match "JavaScript"
        Pattern p = Pattern.compile("(?<![a-z0-9+#])(?:" + alt + ")(?![a-z0-9+#])", Pattern.CASE_INSENSITIVE);
        patterns.put(name, p);
    }

    /** Canonical skill names found in the text, in dictionary order. */
    public Set<String> extract(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return found;
        for (Map.Entry<String, Pattern> e : patterns.entrySet()) {
            if (e.getValue().matcher(text).find()) found.add(e.getKey());
        }
        return found;
    }

    /** Maps a free-form skill (e.g. "springboot") to its canonical name, or returns it unchanged. */
    public String canonical(String skill) {
        Set<String> hit = extract(skill);
        return hit.size() == 1 ? hit.iterator().next() : skill;
    }
}
