package com.jobpilot.repo;

import com.jobpilot.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    boolean existsByExternalId(String externalId);

    Optional<Job> findByExternalId(String externalId);

    List<Job> findAllByOrderByScoreDescCreatedAtDesc();
}
