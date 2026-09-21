package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.simulation.EventCandidate;
import com.example.projectmm.domain.simulation.ValidationResult;
import org.springframework.stereotype.Component;

@Component
public class EventCandidateValidator {

    public ValidationResult validate(EventCandidate candidate) {

        if (candidate.importance() < 0
                || candidate.importance() > 10) {
            return ValidationResult.reject(
                    "importance must be between 0 and 10"
            );
        }

        if (candidate.summary() == null
                || candidate.summary().isBlank()) {
            return ValidationResult.reject(
                    "summary must not be empty"
            );
        }

        if (candidate.requiresAi()) {
            return ValidationResult.reject(
                    "AI processing required"
            );
        }

        return ValidationResult.accept();
    }
}