package com.example.projectmm.application.ai;

import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import com.example.projectmm.domain.simulation.EventCandidate;
import com.example.projectmm.domain.simulation.StateDelta;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiContextBuilderTest {

    private final MemoryRecallService memoryRecallService =
            mock(MemoryRecallService.class);

    private final RelationshipRepository relationshipRepository =
            mock(RelationshipRepository.class);

    private final AiContextBuilder builder =
            new AiContextBuilder(
                    memoryRecallService,
                    relationshipRepository
            );

    @Test
    void 회상된_기억을_AI_Context에_포함한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 21,
                        16, 0
                );

        Character character =
                new Character("Millie");

        CharacterState state =
                new CharacterState(
                        character,
                        80,
                        20,
                        now
                );

        EventCandidate candidate =
                new EventCandidate(
                        now,
                        "MOOD_CHANGE",
                        "기분이 크게 가라앉아 있었다.",
                        4,
                        true,
                        StateDelta.none()
                );

        List<EffectiveMemory> recalled =
                List.of(
                        new EffectiveMemory(
                                1L,
                                "마르바스와 중요한 대화를 나누었다.",
                                8,
                                75,
                                now.minusDays(5),
                                false
                        )
                );

        when(
                memoryRecallService.recall(
                        character.getId(),
                        now,
                        5
                )
        ).thenReturn(recalled);

        when(
                relationshipRepository.findByFromCharacterId(
                        character.getId()
                )
        ).thenReturn(List.of());

        AiContext context =
                builder.build(
                        state,
                        candidate
                );

        assertThat(context.memories())
                .hasSize(1);

        assertThat(
                context.memories()
                        .getFirst()
                        .summary()
        ).isEqualTo(
                "마르바스와 중요한 대화를 나누었다."
        );

        assertThat(
                context.memories()
                        .getFirst()
                        .strength()
        ).isEqualTo(75);
    }

    @Test
    void 암묵적_기억은_구체적인_내용을_AI_Context에_노출하지_않는다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        16, 0
                );

        Character character =
                new Character("Millie");

        CharacterState state =
                new CharacterState(
                        character,
                        80,
                        20,
                        now
                );

        EventCandidate candidate =
                new EventCandidate(
                        now,
                        "MOOD_CHANGE",
                        "기분 변화",
                        4,
                        true,
                        StateDelta.none()
                );

        String secretSummary =
                "마르바스와 구체적인 사건을 겪었다.";

        EffectiveMemory memory =
                new EffectiveMemory(
                        1L,
                        secretSummary,
                        8,
                        75,
                        now.minusDays(5),
                        false,
                        null,
                        null,
                        MemoryAccessLevel.IMPLICIT
                );

        when(
                memoryRecallService.recall(
                        character.getId(),
                        now,
                        5
                )
        ).thenReturn(
                List.of(memory)
        );

        when(
                relationshipRepository.findByFromCharacterId(
                        character.getId()
                )
        ).thenReturn(List.of());

        AiContext context =
                builder.build(
                        state,
                        candidate
                );

        assertThat(context.memories())
                .hasSize(1);

        assertThat(
                context.memories()
                        .getFirst()
                        .summary()
        )
                .doesNotContain(secretSummary)
                .contains("익숙함");
    }

    @Test
    void 잔여_기억은_구체적인_내용을_AI_Context에_노출하지_않는다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        16, 0
                );

        Character character =
                new Character("Millie");

        CharacterState state =
                new CharacterState(
                        character,
                        80,
                        20,
                        now
                );

        EventCandidate candidate =
                new EventCandidate(
                        now,
                        "MOOD_CHANGE",
                        "기분 변화",
                        4,
                        true,
                        StateDelta.none()
                );

        String secretSummary =
                "마르바스와 있었던 구체적인 사건.";

        EffectiveMemory memory =
                new EffectiveMemory(
                        1L,
                        secretSummary,
                        8,
                        75,
                        now.minusDays(5),
                        false,
                        null,
                        null,
                        MemoryAccessLevel.RESIDUAL
                );

        when(
                memoryRecallService.recall(
                        character.getId(),
                        now,
                        5
                )
        ).thenReturn(
                List.of(memory)
        );

        when(
                relationshipRepository.findByFromCharacterId(
                        character.getId()
                )
        ).thenReturn(List.of());

        AiContext context =
                builder.build(
                        state,
                        candidate
                );

        assertThat(context.memories())
                .hasSize(1);

        assertThat(
                context.memories()
                        .getFirst()
                        .summary()
        )
                .doesNotContain(secretSummary)
                .contains("감정적 잔향");
    }

    @Test
    void 차단된_기억이_AI_Context까지_도달하면_실패한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        16, 0
                );

        Character character =
                new Character("Millie");

        CharacterState state =
                new CharacterState(
                        character,
                        80,
                        20,
                        now
                );

        EventCandidate candidate =
                new EventCandidate(
                        now,
                        "MOOD_CHANGE",
                        "기분 변화",
                        4,
                        true,
                        StateDelta.none()
                );

        EffectiveMemory memory =
                new EffectiveMemory(
                        1L,
                        "절대로 전달되면 안 되는 기억",
                        8,
                        75,
                        now.minusDays(5),
                        false,
                        null,
                        null,
                        MemoryAccessLevel.BLOCKED
                );

        when(
                memoryRecallService.recall(
                        character.getId(),
                        now,
                        5
                )
        ).thenReturn(
                List.of(memory)
        );

        when(
                relationshipRepository.findByFromCharacterId(
                        character.getId()
                )
        ).thenReturn(List.of());

        assertThatThrownBy(
                () -> builder.build(
                        state,
                        candidate
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "BLOCKED memory must not reach AI context"
                );
    }
}