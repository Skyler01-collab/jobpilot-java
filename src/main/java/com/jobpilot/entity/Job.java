package com.jobpilot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Job {

    public enum Status { NEW, QUEUED, APPLIED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "remotive:123" - used to avoid duplicates across fetches. */
    @Column(unique = true, length = 600)
    private String externalId;

    private String source;
    private String title;
    private String company;
    private String location;

    @Column(length = 1000)
    private String url;

    @Lob
    private String description;

    private boolean remote;
    private int score;

    @Column(length = 2000)
    private String matchedSkills = "";

    @Column(length = 2000)
    private String missingSkills = "";

    @Enumerated(EnumType.STRING)
    private Status status = Status.NEW;

    private Instant createdAt = Instant.now();
}
