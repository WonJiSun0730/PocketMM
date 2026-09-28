package com.example.projectmm.application.ai;

import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.simulation.StateDelta;
import com.example.projectmm.domain.simulation.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiDecisionValidatorTest {

    private final AiDecisionValidator validator =
            new AiDecisionValidator();

    @Test
    void 관계_변화량이_null이면_거부한다() {

        AiDecision decision =
                new AiDecision(
                        "MOOD_CHANGE",
                        "감정에 변화가 생겼다.",
                        5,
                        new StateDelta(0, 5),
                        null,
                        null
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason())
                .isEqualTo("relationshipDelta must not be null");
    }

    @Test
    void 호감도_변화량이_허용범위를_벗어나면_거부한다() {

        AiDecision decision =
                createDecision(
                        2L,
                        new RelationshipDelta(11, 0, 0)
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason())
                .isEqualTo("affinityChange must be between -10 and 10");
    }

    @Test
    void 신뢰도_변화량이_허용범위를_벗어나면_거부한다() {

        AiDecision decision =
                createDecision(
                        2L,
                        new RelationshipDelta(0, -11, 0)
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason())
                .isEqualTo("trustChange must be between -10 and 10");
    }

    @Test
    void 긴장도_변화량이_허용범위를_벗어나면_거부한다() {

        AiDecision decision =
                createDecision(
                        2L,
                        new RelationshipDelta(0, 0, 11)
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason())
                .isEqualTo("tensionChange must be between -10 and 10");
    }

    @Test
    void 관계가_변했는데_대상이_null이면_거부한다() {

        AiDecision decision =
                createDecision(
                        null,
                        new RelationshipDelta(5, 0, 0)
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason())
                .isEqualTo(
                        "relationshipTargetCharacterId is required when relationship changes"
                );
    }

    @Test
    void 관계가_변하고_대상이_있으면_허용한다() {

        AiDecision decision =
                createDecision(
                        2L,
                        new RelationshipDelta(5, 3, -2)
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void 관계_변화가_없으면_대상이_null이어도_허용한다() {

        AiDecision decision =
                createDecision(
                        null,
                        RelationshipDelta.none()
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void 기억력_변화량이_허용범위를_벗어나면_거부한다() {

        AiDecision decision =
                new AiDecision(
                        "MEMORY_CHANGE",
                        "기억력에 변화가 생겼다.",
                        5,
                        new StateDelta(
                                0,
                                0,
                                -21
                        ),
                        null,
                        RelationshipDelta.none()
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid())
                .isFalse();

        assertThat(result.reason())
                .isEqualTo(
                        "memoryAbilityChange must be between -20 and 20"
                );
    }

    @Test
    void 기억력_변화량이_허용범위_안이면_허용한다() {

        AiDecision decision =
                new AiDecision(
                        "MEMORY_CHANGE",
                        "기억력에 변화가 생겼다.",
                        5,
                        new StateDelta(
                                0,
                                0,
                                -20
                        ),
                        null,
                        RelationshipDelta.none()
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid())
                .isTrue();
    }

    @Test
    void narrativeAnchor가_허용범위를_벗어나면_거부한다() {

        AiDecision decision =
                new AiDecision(
                        "MEMORY_CHANGE",
                        "장기 서사에 중요한 사건이 발생했다.",
                        5,
                        11,
                        StateDelta.none(),
                        null,
                        RelationshipDelta.none(),
                        null
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid())
                .isFalse();

        assertThat(result.reason())
                .isEqualTo(
                        "narrativeAnchor must be between 0 and 10"
                );
    }

    @Test
    void narrativeAnchor가_허용범위_안이면_허용한다() {

        AiDecision decision =
                new AiDecision(
                        "MEMORY_CHANGE",
                        "장기 서사에 중요한 사건이 발생했다.",
                        5,
                        10,
                        StateDelta.none(),
                        null,
                        RelationshipDelta.none(),
                        null
                );

        ValidationResult result =
                validator.validate(decision);

        assertThat(result.valid())
                .isTrue();
    }

    private AiDecision createDecision(
            Long relationshipTargetCharacterId,
            RelationshipDelta relationshipDelta
    ) {
        return new AiDecision(
                "MOOD_CHANGE",
                "감정에 변화가 생겼다.",
                5,
                new StateDelta(0, 5),
                relationshipTargetCharacterId,
                relationshipDelta
        );
    }
}