package com.example.projectmm.application.ai;

import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.simulation.ValidationResult;
import org.springframework.stereotype.Component;

@Component
public class AiDecisionValidator {

    public ValidationResult validate(AiDecision decision) {

        if (decision == null) {
            return ValidationResult.reject(
                    "AI decision must not be null"
            );
        }

        if (decision.eventType() == null
                || decision.eventType().isBlank()) {
            return ValidationResult.reject(
                    "eventType must not be empty"
            );
        }

        if (decision.summary() == null
                || decision.summary().isBlank()) {
            return ValidationResult.reject(
                    "summary must not be empty"
            );
        }

        if (decision.importance() < 0
                || decision.importance() > 10) {
            return ValidationResult.reject(
                    "importance must be between 0 and 10"
            );
        }

        if (decision.stateDelta() == null) {
            return ValidationResult.reject(
                    "stateDelta must not be null"
            );
        }

        if (Math.abs(decision.stateDelta().energyChange()) > 20) {
            return ValidationResult.reject(
                    "energyChange must be between -20 and 20"
            );
        }

        if (Math.abs(decision.stateDelta().moodChange()) > 20) {
            return ValidationResult.reject(
                    "moodChange must be between -20 and 20"
            );
        }

        if (Math.abs(
                decision.stateDelta().memoryAbilityChange()
        ) > 20) {
            return ValidationResult.reject(
                    "memoryAbilityChange must be between -20 and 20"
            );
        }

        if (decision.relationshipDelta() == null) {
            return ValidationResult.reject(
                    "relationshipDelta must not be null"
            );
        }

        if (Math.abs(decision.relationshipDelta().affinityChange()) > 10) {
            return ValidationResult.reject(
                    "affinityChange must be between -10 and 10"
            );
        }

        if (Math.abs(decision.relationshipDelta().trustChange()) > 10) {
            return ValidationResult.reject(
                    "trustChange must be between -10 and 10"
            );
        }

        if (Math.abs(decision.relationshipDelta().tensionChange()) > 10) {
            return ValidationResult.reject(
                    "tensionChange must be between -10 and 10"
            );
        }

        boolean hasRelationshipChange =
                decision.relationshipDelta().affinityChange() != 0
                        || decision.relationshipDelta().trustChange() != 0
                        || decision.relationshipDelta().tensionChange() != 0;

        if (hasRelationshipChange
                && decision.relationshipTargetCharacterId() == null) {
            return ValidationResult.reject(
                    "relationshipTargetCharacterId is required when relationship changes"
            );
        }

        return ValidationResult.accept();
    }
}