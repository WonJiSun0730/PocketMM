package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.scene.Scene;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.*;

class MemoryRecallServiceTest {

    private final MemoryRepository memoryRepository =
            mock(MemoryRepository.class);

    private final MemoryDecayPolicy memoryDecayPolicy =
            new MemoryDecayPolicy();

    private final MemoryCompressionService memoryCompressionService =
            mock(MemoryCompressionService.class);

    private final CharacterStateRepository characterStateRepository =
            mock(CharacterStateRepository.class);

    private final MemoryRecallService memoryRecallService =
            new MemoryRecallService(
                    memoryRepository,
                    memoryDecayPolicy,
                    memoryCompressionService,
                    characterStateRepository
            );

    @Test
    void 현재_강도가_높은_기억부터_회상하고_소멸한_기억은_제외한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 21,
                        12, 0
                );

        Memory recentMemory =
                createMemory(
                        "최근의 평범한 기억",
                        5,
                        50,
                        now.minusDays(1)
                );

        Memory importantOldMemory =
                createMemory(
                        "오래됐지만 중요한 기억",
                        9,
                        90,
                        now.minusDays(20)
                );

        Memory fadedMemory =
                createMemory(
                        "거의 잊힌 오래된 기억",
                        5,
                        40,
                        now.minusDays(30)
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(1L)
        ).thenReturn(
                List.of(
                        recentMemory,
                        importantOldMemory,
                        fadedMemory
                )
        );

        List<EffectiveMemory> result =
                memoryRecallService.recall(
                        1L,
                        now,
                        10
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result.get(0).summary())
                .isEqualTo(
                        "오래됐지만 중요한 기억"
                );

        assertThat(result.get(0).effectiveStrength())
                .isEqualTo(70);

        assertThat(result.get(1).summary())
                .isEqualTo(
                        "최근의 평범한 기억"
                );

        assertThat(result.get(1).effectiveStrength())
                .isEqualTo(48);

        assertThat(result)
                .noneMatch(memory ->
                        memory.summary()
                                .equals("거의 잊힌 오래된 기억")
                );
    }

    @Test
    void 회상할_기억의_개수를_제한한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 21,
                        12, 0
                );

        Memory strongest =
                createMemory(
                        "가장 강한 기억",
                        9,
                        90,
                        now.minusDays(1)
                );

        Memory middle =
                createMemory(
                        "중간 기억",
                        7,
                        70,
                        now.minusDays(1)
                );

        Memory weakest =
                createMemory(
                        "약한 기억",
                        5,
                        50,
                        now.minusDays(1)
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(1L)
        ).thenReturn(
                List.of(
                        weakest,
                        strongest,
                        middle
                )
        );

        List<EffectiveMemory> result =
                memoryRecallService.recall(
                        1L,
                        now,
                        2
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result.get(0).summary())
                .isEqualTo("가장 강한 기억");

        assertThat(result.get(1).summary())
                .isEqualTo("중간 기억");
    }

    @Test
    void 희미하지만_남아있는_기억은_회상_과정에서_압축을_요청한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 21,
                        12, 0
                );

        Memory fadingMemory =
                createMemory(
                        "희미해지는 기억",
                        5,
                        50,
                        now.minusDays(10)
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(1L)
        ).thenReturn(
                List.of(fadingMemory)
        );

        memoryRecallService.recall(
                1L,
                now,
                10
        );

        verify(memoryCompressionService)
                .compressIfNeeded(
                        fadingMemory,
                        now
                );
    }

    @Test
    void 구조화된_기억의_핵심값이_현재_질문과_관련되면_우선_회상한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Memory relevant =
                createStructuredMemory(
                        "사용자가 좋아하는 음식을 이야기했다.",
                        "나는 오리고기를 제일 좋아해",
                        new com.example.projectmm.domain.memory.MemoryFact(
                                com.example.projectmm.domain.memory.MemoryFact.Kind.PREFERENCE,
                                "나",
                                "좋아해",
                                "오리고기"
                        ),
                        5,
                        now.minusDays(3)
                );

        Memory strongerButIrrelevant =
                createStructuredMemory(
                        "사용자가 최근 여행 이야기를 했다.",
                        "나는 제주도 여행이 정말 기억에 남아",
                        new com.example.projectmm.domain.memory.MemoryFact(
                                com.example.projectmm.domain.memory.MemoryFact.Kind.REPORTED_EVENT,
                                "나",
                                "기억에 남아",
                                "제주도 여행"
                        ),
                        9,
                        now.minusDays(1)
                );

        when(
                memoryRepository.findForConversation(
                        1L,
                        10L
                )
        ).thenReturn(
                List.of(
                        strongerButIrrelevant,
                        relevant
                )
        );

        List<EffectiveMemory> result =
                memoryRecallService.recallForConversation(
                        1L,
                        10L,
                        now,
                        2,
                        "내가 좋아하는 음식 뭐였지?"
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result.get(0).summary())
                .contains("오리고기");
    }

    @Test
    void 직접_키워드가_없어도_선호_질문에는_선호_기억을_우선_회상한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Memory preference =
                createStructuredMemory(
                        "unused",
                        "나는 오리고기를 제일 좋아해",
                        new com.example.projectmm.domain.memory.MemoryFact(
                                com.example.projectmm.domain.memory.MemoryFact.Kind.PREFERENCE,
                                "나",
                                "좋아해",
                                "오리고기"
                        ),
                        5,
                        now.minusDays(3)
                );

        Memory strongerButIrrelevant =
                createStructuredMemory(
                        "unused",
                        "나는 제주도 여행이 정말 기억에 남아",
                        new com.example.projectmm.domain.memory.MemoryFact(
                                com.example.projectmm.domain.memory.MemoryFact.Kind.REPORTED_EVENT,
                                "나",
                                "기억에 남아",
                                "제주도 여행"
                        ),
                        9,
                        now.minusDays(1)
                );

        when(
                memoryRepository.findForConversation(
                        1L,
                        10L
                )
        ).thenReturn(
                List.of(
                        strongerButIrrelevant,
                        preference
                )
        );

        List<EffectiveMemory> result =
                memoryRecallService.recallForConversation(
                        1L,
                        10L,
                        now,
                        2,
                        "내 음식 취향 기억나?"
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result.get(0).summary())
                .contains("오리고기");
    }

    @Test
    void 사건_질문에는_더_강한_선호_기억보다_사건_기억을_우선_회상한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Memory reportedEvent =
                createStructuredMemory(
                        "unused",
                        "나는 제주도에서 지갑을 잃어버렸어",
                        new com.example.projectmm.domain.memory.MemoryFact(
                                com.example.projectmm.domain.memory.MemoryFact.Kind.REPORTED_EVENT,
                                "나",
                                "잃어버렸어",
                                "지갑"
                        ),
                        5,
                        now.minusDays(3)
                );

        Memory strongerPreference =
                createStructuredMemory(
                        "unused",
                        "나는 오리고기를 제일 좋아해",
                        new com.example.projectmm.domain.memory.MemoryFact(
                                com.example.projectmm.domain.memory.MemoryFact.Kind.PREFERENCE,
                                "나",
                                "좋아해",
                                "오리고기"
                        ),
                        9,
                        now.minusDays(1)
                );

        when(
                memoryRepository.findForConversation(
                        1L,
                        10L
                )
        ).thenReturn(
                List.of(
                        strongerPreference,
                        reportedEvent
                )
        );

        List<EffectiveMemory> result =
                memoryRecallService.recallForConversation(
                        1L,
                        10L,
                        now,
                        2,
                        "제주도에서 무슨 일이 있었지?"
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result.get(0).summary())
                .contains("지갑");
    }

    @Test
    void 구체적인_키워드_일치는_일반적인_fact_kind_보너스에_묻히지_않는다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Memory duckPreference =
                createStructuredMemory(
                        "unused",
                        "나는 오리고기를 제일 좋아해",
                        new com.example.projectmm.domain.memory.MemoryFact(
                                com.example.projectmm.domain.memory.MemoryFact.Kind.PREFERENCE,
                                "나",
                                "좋아해",
                                "오리고기"
                        ),
                        5,
                        now.minusDays(3)
                );

        Memory unrelatedEvent =
                createStructuredMemory(
                        "unused",
                        "나는 제주도에서 지갑을 잃어버렸어",
                        new com.example.projectmm.domain.memory.MemoryFact(
                                com.example.projectmm.domain.memory.MemoryFact.Kind.REPORTED_EVENT,
                                "나",
                                "잃어버렸어",
                                "지갑"
                        ),
                        9,
                        now.minusDays(1)
                );

        when(
                memoryRepository.findForConversation(
                        1L,
                        10L
                )
        ).thenReturn(
                List.of(
                        unrelatedEvent,
                        duckPreference
                )
        );

        List<EffectiveMemory> result =
                memoryRecallService.recallForConversation(
                        1L,
                        10L,
                        now,
                        2,
                        "오리고기 얘기했었지?"
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result.get(0).summary())
                .contains("오리고기");
    }

    @Test
    void 기억력이_낮아지면_약한_기억은_회상하지_못하지만_강한_기억은_유지한다() {

        Long characterId = 1L;

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Character character =
                new Character("Millie");

        CharacterState state =
                new CharacterState(
                        character,
                        70,
                        70,
                        40,
                        now
                );

        when(
                characterStateRepository.findByCharacterId(
                        characterId
                )
        ).thenReturn(
                Optional.of(state)
        );

        Memory weakMemory =
                createMemory(
                        "약한 기억",
                        5,
                        50,
                        now
                );

        Memory strongMemory =
                createMemory(
                        "강한 기억",
                        9,
                        90,
                        now
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        )
        ).thenReturn(
                List.of(
                        weakMemory,
                        strongMemory
                )
        );

        List<EffectiveMemory> result =
                memoryRecallService.recall(
                        characterId,
                        now,
                        10
                );

        assertThat(result)
                .hasSize(1);

        assertThat(result.get(0).summary())
                .isEqualTo("강한 기억");

        assertThat(result.get(0).effectiveStrength())
                .isEqualTo(90);
    }

    @Test
    void 상태_변화로_기억력이_낮아지면_기존에_회상되던_기억이_제외된다() {

        Long characterId = 1L;

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Character character =
                new Character("Millie");

        CharacterState state =
                new CharacterState(
                        character,
                        70,
                        70,
                        80,
                        now
                );

        when(
                characterStateRepository.findByCharacterId(
                        characterId
                )
        ).thenReturn(
                Optional.of(state)
        );

        Memory memory =
                createMemory(
                        "경계에 있는 기억",
                        5,
                        50,
                        now
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        )
        ).thenReturn(
                List.of(memory)
        );

        List<EffectiveMemory> before =
                memoryRecallService.recall(
                        characterId,
                        now,
                        10
                );

        assertThat(before)
                .hasSize(1);

        state.apply(
                new com.example.projectmm.domain.simulation.StateDelta(
                        0,
                        0,
                        -40
                )
        );

        assertThat(state.getMemoryAbility())
                .isEqualTo(40);

        List<EffectiveMemory> after =
                memoryRecallService.recall(
                        characterId,
                        now,
                        10
                );

        assertThat(after)
                .isEmpty();
    }

    @Test
    void 기억력_상태가_없으면_기존_회상_동작을_유지한다() {

        Long characterId = 1L;

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        when(
                characterStateRepository.findByCharacterId(
                        characterId
                )
        ).thenReturn(
                Optional.empty()
        );

        Memory weakMemory =
                createMemory(
                        "약하지만 남아있는 기억",
                        5,
                        10,
                        now
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        )
        ).thenReturn(
                List.of(weakMemory)
        );

        List<EffectiveMemory> result =
                memoryRecallService.recall(
                        characterId,
                        now,
                        10
                );

        assertThat(result)
                .hasSize(1);

        assertThat(result.get(0).summary())
                .isEqualTo("약하지만 남아있는 기억");

        assertThat(result.get(0).effectiveStrength())
                .isEqualTo(10);
    }

    private Memory createMemory(
            String summary,
            int importance,
            int strength,
            LocalDateTime rememberedAt
    ) {
        Character character =
                new Character("Millie");

        Scene scene =
                new Scene(
                        rememberedAt,
                        "기억 테스트",
                        "DAILY"
                );

        Event event =
                new Event(
                        scene,
                        rememberedAt,
                        "INTERACTION",
                        summary,
                        importance
                );

        return new Memory(
                character,
                event,
                summary,
                importance,
                strength,
                rememberedAt
        );
    }

    private Memory createStructuredMemory(
            String summary,
            String quote,
            com.example.projectmm.domain.memory.MemoryFact fact,
            int importance,
            LocalDateTime rememberedAt
    ) {
        Character character =
                new Character("Millie");

        var conversation =
                com.example.projectmm.domain.chat.Conversation.fresh(
                        character,
                        rememberedAt
                );

        var source =
                new com.example.projectmm.domain.chat.ChatMessage(
                        conversation,
                        character,
                        com.example.projectmm.domain.chat.ChatRole.USER,
                        quote,
                        com.example.projectmm.application.policy.ContentMode.NORMAL,
                        rememberedAt
                );

        return Memory.fromChat(
                conversation,
                source,
                quote,
                importance,
                rememberedAt,
                "test-" + quote.hashCode(),
                fact
        );
    }
}