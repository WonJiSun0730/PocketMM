package com.example.projectmm.application.ai;

import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.memory.MemoryTier;
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
                                false,
                                null,
                                null,
                                MemoryAccessLevel.EXPLICIT,
                                MemoryTier.HOT
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

    @Test
    void HOT_기억은_AI_Context에_전체_메타정보를_유지한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 29,
                        12, 0
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
                        "TEST",
                        "test",
                        4,
                        true,
                        StateDelta.none()
                );

        EffectiveMemory memory =
                new EffectiveMemory(
                        1L,
                        "최근의 중요한 기억",
                        8,
                        75,
                        now.minusDays(1),
                        false,
                        null,
                        null,
                        MemoryAccessLevel.EXPLICIT,
                        MemoryTier.HOT
                );

        when(
                memoryRecallService.recall(
                        character.getId(),
                        now,
                        5
                )
        ).thenReturn(List.of(memory));

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

        assertThat(context.memories().getFirst().importance())
                .isEqualTo(8);

        assertThat(context.memories().getFirst().strength())
                .isEqualTo(75);
    }

    @Test
    void WARM_기억은_AI_Context에서_strength를_제거한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 29,
                        12, 0
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
                        "TEST",
                        "test",
                        4,
                        true,
                        StateDelta.none()
                );

        EffectiveMemory memory =
                new EffectiveMemory(
                        1L,
                        "일반 장기 기억",
                        6,
                        55,
                        now.minusDays(20),
                        false,
                        null,
                        null,
                        MemoryAccessLevel.EXPLICIT,
                        MemoryTier.WARM
                );

        when(
                memoryRecallService.recall(
                        character.getId(),
                        now,
                        5
                )
        ).thenReturn(List.of(memory));

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

        assertThat(context.memories().getFirst().summary())
                .isEqualTo("일반 장기 기억");

        assertThat(context.memories().getFirst().importance())
                .isEqualTo(6);

        assertThat(context.memories().getFirst().strength())
                .isZero();
    }

    @Test
    void COLD_기억은_AI_Context에_압축된_summary만_유지한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 29,
                        12, 0
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
                        "TEST",
                        "test",
                        4,
                        true,
                        StateDelta.none()
                );

        EffectiveMemory memory =
                new EffectiveMemory(
                        1L,
                        "압축된 장기 패턴",
                        9,
                        90,
                        now.minusDays(100),
                        false,
                        null,
                        null,
                        MemoryAccessLevel.EXPLICIT,
                        MemoryTier.COLD
                );

        when(
                memoryRecallService.recall(
                        character.getId(),
                        now,
                        5
                )
        ).thenReturn(List.of(memory));

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

        assertThat(context.memories().getFirst().summary())
                .isEqualTo("압축된 장기 패턴");

        assertThat(context.memories().getFirst().importance())
                .isZero();

        assertThat(context.memories().getFirst().strength())
                .isZero();
    }

    @Test
    void HOT이어도_IMPLICIT_기억은_원문을_AI_Context에_노출하지_않는다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 29,
                        12, 0
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
                        "TEST",
                        "test",
                        4,
                        true,
                        StateDelta.none()
                );

        String secretSummary =
                "절대로 그대로 노출되면 안 되는 구체적인 사건";

        EffectiveMemory memory =
                new EffectiveMemory(
                        1L,
                        secretSummary,
                        9,
                        90,
                        now.minusDays(1),
                        false,
                        null,
                        null,
                        MemoryAccessLevel.IMPLICIT,
                        MemoryTier.HOT
                );

        when(
                memoryRecallService.recall(
                        character.getId(),
                        now,
                        5
                )
        ).thenReturn(List.of(memory));

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

        assertThat(
                context.memories()
                        .getFirst()
                        .strength()
        ).isEqualTo(90);
    }

    @Test
    void HOT이어도_RESIDUAL_기억은_원문을_AI_Context에_노출하지_않는다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 29,
                        12, 0
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
                        "TEST",
                        "test",
                        4,
                        true,
                        StateDelta.none()
                );

        String secretSummary =
                "구체적인 사건과 인물에 대한 실제 기억";

        EffectiveMemory memory =
                new EffectiveMemory(
                        1L,
                        secretSummary,
                        9,
                        90,
                        now.minusDays(1),
                        false,
                        null,
                        null,
                        MemoryAccessLevel.RESIDUAL,
                        MemoryTier.HOT
                );

        when(
                memoryRecallService.recall(
                        character.getId(),
                        now,
                        5
                )
        ).thenReturn(List.of(memory));

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

        assertThat(
                context.memories()
                        .getFirst()
                        .strength()
        ).isEqualTo(90);
    }

    @Test
    void COLD여도_BLOCKED_기억은_AI_Context에_진입할_수_없다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 29,
                        12, 0
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
                        "TEST",
                        "test",
                        4,
                        true,
                        StateDelta.none()
                );

        EffectiveMemory memory =
                new EffectiveMemory(
                        1L,
                        "차단된 기억의 실제 내용",
                        9,
                        90,
                        now.minusDays(100),
                        false,
                        null,
                        null,
                        MemoryAccessLevel.BLOCKED,
                        MemoryTier.COLD
                );

        when(
                memoryRecallService.recall(
                        character.getId(),
                        now,
                        5
                )
        ).thenReturn(List.of(memory));

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
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "BLOCKED memory must not reach AI context"
                );
    }
}