package com.jobpilot.web;

import com.jobpilot.entity.SavedAnswer;
import com.jobpilot.repo.AnswerRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/answers")
public class AnswerController {

    public record AnswerRequest(String question, String answer) {}

    private final AnswerRepository repo;

    public AnswerController(AnswerRepository repo) {
        this.repo = repo;
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    @GetMapping
    public List<SavedAnswer> list() {
        return repo.findAllByOrderByQuestionAsc();
    }

    /** Saves or updates an answer. An empty answer removes it. */
    @PutMapping
    public Map<String, Boolean> save(@RequestBody AnswerRequest r) {
        String key = norm(r.question());
        if (key.length() < 3) throw new IllegalArgumentException("Question is too short");
        if (key.length() > 300) key = key.substring(0, 300);
        SavedAnswer a = repo.findByQkey(key).orElse(null);
        String ans = r.answer() == null ? "" : r.answer().strip();
        if (ans.isEmpty()) {
            if (a != null) repo.delete(a);
            return Map.of("saved", false);
        }
        if (a == null) {
            a = new SavedAnswer();
            a.setQkey(key);
        }
        String q = r.question().strip();
        a.setQuestion(q.length() > 500 ? q.substring(0, 500) : q);
        a.setAnswer(ans.length() > 2000 ? ans.substring(0, 2000) : ans);
        repo.save(a);
        return Map.of("saved", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return Map.of("deleted", true);
    }
}
