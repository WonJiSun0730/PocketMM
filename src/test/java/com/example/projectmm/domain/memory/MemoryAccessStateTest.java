package com.example.projectmm.domain.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.scene.Scene;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryAccessStateTest {

    @Test
    void 기억의_접근_수준을_변경할_수_있다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        LocalDateTime changedAt =
                rememberedAt.plusHours(1);

        Memory memory =
                createMemory(
                        rememberedAt
                );

        MemoryAccessState state =
                new MemoryAccessState(
                        memory,
                        MemoryAccessLevel.EXPLICIT,
                        rememberedAt
                );

        state.changeAccessLevel(
                MemoryAccessLevel.IMPLICIT,
                changedAt
        );

        assertThat(state.getAccessLevel())
                .isEqualTo(
                        MemoryAccessLevel.IMPLICIT
                );

        assertThat(state.getUpdatedAt())
                .isEqualTo(changedAt);
    }

    @Test
    void 기억은_삭제하지_않고_접근만_차단할_수_있다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Memory memory =
                createMemory(now);

        MemoryAccessState state =
                new MemoryAccessState(
                        memory,
                        MemoryAccessLevel.BLOCKED,
                        now
                );

        assertThat(state.getMemory())
                .isSameAs(memory);

        assertThat(state.getAccessLevel())
                .isEqualTo(
                        MemoryAccessLevel.BLOCKED
                );
    }

    @Test
    void 접근_수준은_null일_수_없다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Memory memory =
                createMemory(now);

        assertThatThrownBy(
                () -> new MemoryAccessState(
                        memory,
                        null,
                        now
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "accessLevel must not be null"
                );
    }

    @Test
    void 접근_수준_변경시_null은_허용하지_않는다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Memory memory =
                createMemory(now);

        MemoryAccessState state =
                new MemoryAccessState(
                        memory,
                        MemoryAccessLevel.EXPLICIT,
                        now
                );

        assertThatThrownBy(
                () -> state.changeAccessLevel(
                        null,
                        now.plusHours(1)
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "accessLevel must not be null"
                );
    }

    private Memory createMemory(
            LocalDateTime rememberedAt
    ) {

        Character character =
                new Character("Millie");

        Scene scene =
                new Scene(
                        rememberedAt,
                        "기억 접근 테스트",
                        "DAILY"
                );

        Event event =
                new Event(
                        scene,
                        rememberedAt,
                        "INTERACTION",
                        "중요한 사건이 있었다.",
                        8
                );

        return new Memory(
                character,
                event,
                "중요한 사건이 있었다.",
                8,
                80,
                rememberedAt
        );
    }
}