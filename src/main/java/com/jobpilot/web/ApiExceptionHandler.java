package com.jobpilot.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> notFound(NoResourceFoundException e) {
        return ResponseEntity.notFound().build();
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> other(Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
    }
}
