package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.simulation.CatchUpSegment;
import com.example.projectmm.domain.simulation.SegmentPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CatchUpEventCandidateServiceTest {

    private final CatchUpEventCandidateService service =
            new CatchUpEventCandidateService();

    @Test
    void 평범한_상태에서도_맥락기반_생활사건_판단_후보를_생성한다() {
        LocalDateTime start =
                LocalDateTime.of(
                        2026,
                        9,
                        30,
                        12,
                        0
                );

        Character character =
                new Character("Millie");

        CharacterState state =
                new CharacterState(
                        character,
                        70,
                        70,
                        start
                );

        CatchUpSegment segment =
                new CatchUpSegment(
                        start,
                        start.plusHours(6),
                        SegmentPeriod.DAYTIME
                );

        var candidates =
                service.generate(
                        state,
                        segment
                );

        assertThat(candidates)
                .hasSize(1);

        var candidate =
                candidates.getFirst();

        assertThat(candidate.eventType())
                .isEqualTo("DAILY_ACTIVITY");

        assertThat(candidate.requiresAi())
                .isTrue();

        assertThat(candidate.stateDelta())
                .isEqualTo(
                        com.example.projectmm.domain.simulation.StateDelta.none()
                );
    }
}