package com.example.projectmm.application.ai;

import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.memory.MemoryAccessChange;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.simulation.StateDelta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAccessDecisionValidatorTest {
    private final AiDecisionValidator validator = new AiDecisionValidator();
    private final LocalDateTime at = LocalDateTime.of(2030, 1, 2, 12, 0);

    @Test
    void legacyDecisionNeedsNoAccessCommandOrEventTime() {
        assertThat(validator.validate(new AiDecision("ANY", "event", 5,
                new StateDelta(0, 0, -5), null, RelationshipDelta.none())).valid()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"ALL,EXPLICIT,", "ALL,IMPLICIT,", "ALL,RESIDUAL,", "ALL,BLOCKED,",
            "SINCE,BLOCKED,2030-01-02T12:00:00", "SINCE,EXPLICIT,2030-01-01T00:00:00"})
    void acceptsExplicitCommands(String scope, String level, String since) {
        assertThat(validator.validate(decision(scope, level, since), at).valid()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"ALL,UNKNOWN,", "ALL,,", ",BLOCKED,", "MEMORY,BLOCKED,", "SINCE,BLOCKED,",
            "ALL,BLOCKED,2030-01-01T00:00:00", "SINCE,BLOCKED,yesterday",
            "SINCE,BLOCKED,2030-01-03T00:00:00", "SINCE,BLOCKED,2030-02-30T00:00:00",
            "SINCE,BLOCKED,0000-01-01T00:00:00", "SINCE,BLOCKED,2030-01-01"})
    void rejectsInvalidLevelScopeAndCutoffs(String scope, String level, String since) {
        assertThat(validator.validate(decision(scope, level, since), at).valid()).isFalse();
    }

    @Test
    void accessCommandRequiresTrustedEventTime() {
        assertThat(validator.validate(decision("ALL", "BLOCKED", null)).valid()).isFalse();
    }

    private AiDecision decision(String scope, String level, String since) {
        return new AiDecision("ANY", "event", 5, StateDelta.none(), null, RelationshipDelta.none(),
                new MemoryAccessChange(scope, level, since));
    }
}
