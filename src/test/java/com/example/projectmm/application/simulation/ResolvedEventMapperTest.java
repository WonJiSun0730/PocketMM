package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.simulation.EventSource;
import com.example.projectmm.domain.simulation.ResolvedEvent;
import com.example.projectmm.domain.simulation.StateDelta;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedEventMapperTest {

    private final ResolvedEventMapper mapper =
            new ResolvedEventMapper();

    @Test
    void 확정된_사건을_Event_엔티티로_변환한다() {

        LocalDateTime occurredAt =
                LocalDateTime.of(
                        2026, 9, 21,
                        13, 0
                );

        Scene scene =
                new Scene(
                        occurredAt,
                        "평범한 오후",
                        "DAILY"
                );

        ResolvedEvent resolvedEvent =
                new ResolvedEvent(
                        occurredAt,
                        "REFLECTION",
                        "밀리는 잠시 자신의 감정을 돌아보았다.",
                        4,
                        new StateDelta(0, 5),
                        null,
                        RelationshipDelta.none(),
                        EventSource.AI
                );

        Event event =
                mapper.toEntity(
                        scene,
                        resolvedEvent
                );

        assertThat(event.getScene())
                .isSameAs(scene);

        assertThat(event.getOccurredAt())
                .isEqualTo(occurredAt);

        assertThat(event.getEventType())
                .isEqualTo("REFLECTION");

        assertThat(event.getSummary())
                .isEqualTo(
                        "밀리는 잠시 자신의 감정을 돌아보았다."
                );

        assertThat(event.getImportance())
                .isEqualTo(4);

        assertThat(event.getEnergyChange())
                .isZero();

        assertThat(event.getMoodChange())
                .isEqualTo(5);

        assertThat(event.getMemoryAbilityChange())
                .isZero();
    }
}