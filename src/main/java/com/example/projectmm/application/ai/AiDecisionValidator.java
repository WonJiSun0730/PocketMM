package com.example.projectmm.application.ai;

import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.simulation.ValidationResult;
import org.springframework.stereotype.Component;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Component
public class AiDecisionValidator {

    public ValidationResult validate(AiDecision decision) {
        return validate(decision, null);
    }

    public ValidationResult validate(AiDecision decision, LocalDateTime occurredAt) {

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

        if (decision.narrativeAnchor() < 0
                || decision.narrativeAnchor() > 10) {
            return ValidationResult.reject(
                    "narrativeAnchor must be between 0 and 10"
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

        var access = decision.memoryAccessChange();
        if (access != null) {
            if (occurredAt == null || access.accessLevel() == null) {
                return ValidationResult.reject("Memory access requires event time and access level");
            }
            try {
                MemoryAccessLevel.valueOf(access.accessLevel());
            } catch (IllegalArgumentException ex) {
                return ValidationResult.reject("Invalid memory access level");
            }
            if ("ALL".equals(access.scope())) {
                if (access.since() != null) return ValidationResult.reject("ALL must not specify since");
            } else if ("SINCE".equals(access.scope())) {
                if (access.since() == null) return ValidationResult.reject("SINCE requires since");
                try {
                    var since = LocalDateTime.parse(access.since());
                    if (since.getYear() < 1 || since.getYear() > 9999 || since.isAfter(occurredAt)) {
                        return ValidationResult.reject("since must be a supported time no later than the event");
                    }
                } catch (DateTimeParseException ex) {
                    return ValidationResult.reject("Invalid memory access since timestamp");
                }
            } else {
                return ValidationResult.reject("Memory access scope must be ALL or SINCE");
            }
        }
        return ValidationResult.accept();
    }
}
