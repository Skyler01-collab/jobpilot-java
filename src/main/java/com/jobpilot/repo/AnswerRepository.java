package com.jobpilot.repo;

import com.jobpilot.entity.SavedAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<SavedAnswer, Long> {
    Optional<SavedAnswer> findByQkey(String qkey);

    List<SavedAnswer> findAllByOrderByQuestionAsc();
}
