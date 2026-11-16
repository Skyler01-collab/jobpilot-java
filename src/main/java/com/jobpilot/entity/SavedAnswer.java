package com.jobpilot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An answer to a form question, remembered so it can be filled automatically next time. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SavedAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalised question text (lowercase words) used for matching. */
    @Column(unique = true, length = 300)
    private String qkey;

    @Column(length = 500)
    private String question;

    @Column(length = 2000)
    private String answer;
}
