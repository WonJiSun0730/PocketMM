package com.example.projectmm.application.memory;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class MemoryScenarioEvaluationTest {

    private final MemoryRepository memoryRepository =
            mock(MemoryRepository.class);

    private final MemoryCompressionService memoryCompressionService =
            mock(MemoryCompressionService.class);

    private final CharacterStateRepository characterStateRepository =
            mock(CharacterStateRepository.class);

    private final MemoryAccessStateRepository memoryAccessStateRepository =
            mock(MemoryAccessStateRepository.class);

    private final MemoryLifecycleService memoryLifecycleService =
            mock(MemoryLifecycleService.class);

    private final MemoryProvenanceAccessPolicy provenanceAccess =
            mock(MemoryProvenanceAccessPolicy.class);

    private final MemoryRecallService memoryRecallService =
            new MemoryRecallService(
                    memoryRepository,
                    new MemoryDecayPolicy(),
                    memoryCompressionService,
                    characterStateRepository,
                    memoryAccessStateRepository,
                    memoryLifecycleService,
                    provenanceAccess
            );

    private final LocalDateTime now =
            LocalDateTime.of(
                    2026, 9, 29,
                    12, 0
            );

    @Test
    void 시나리오_미래_기억은_강해도_현재_회상에서_제외된다() {

        Memory valid =
                memory(
                        "나는 오리고기를 가장 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "오리고기",
                        8,
                        now.minusDays(3)
                );

        Memory future =
                memory(
                        "나는 내일 부산에 도착했어",
                        MemoryFact.Kind.REPORTED_EVENT,
                        "나",
                        "도착했어",
                        "부산",
                        10,
                        now.plusDays(1)
                );

        when(
                memoryRepository.findForConversation(
                        1L,
                        10L
                )
        ).thenReturn(
                List.of(
                        future,
                        valid
                )
        );

        when(
                provenanceAccess.restrictions(
                        anyList()
                )
        ).thenReturn(
                Map.of()
        );

        List<EffectiveMemory> result =
                memoryRecallService.recallForConversation(
                        1L,
                        10L,
                        now,
                        5,
                        "내가 좋아하는 음식 기억나?"
                );

        assertThat(result)
                .extracting(EffectiveMemory::summary)
                .anyMatch(
                        summary ->
                                summary.contains(
                                        "나는 오리고기를 가장 좋아해"
                                )
                )
                .noneMatch(
                        summary ->
                                summary.contains(
                                        "나는 내일 부산에 도착했어"
                                )
                );
    }

    @Test
    void 시나리오_독립_시작에서는_과거_공유기억이_강해도_격리된다() {

        Memory conversationMemory =
                memory(
                        "나는 홍차를 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "홍차",
                        6,
                        now.minusDays(2)
                );

        Memory sharedMemory =
                worldMemory(
                        "우리는 과거 세계에서 함께 바다를 보았다",
                        10,
                        now.minusHours(1)
                );

        when(
                memoryRepository.findForConversation(
                        1L,
                        10L
                )
        ).thenReturn(
                List.of(
                        sharedMemory,
                        conversationMemory
                )
        );

        when(
                provenanceAccess.restrictions(
                        anyList()
                )
        ).thenReturn(
                Map.of()
        );

        List<EffectiveMemory> result =
                memoryRecallService.recallForConversation(
                        1L,
                        10L,
                        now,
                        5,
                        "과거에 우리 같이 뭘 했지?",
                        false
                );

        assertThat(result)
                .extracting(EffectiveMemory::summary)
                .anyMatch(
                        summary ->
                                summary.contains(
                                        "나는 홍차를 좋아해"
                                )
                )
                .noneMatch(
                        summary ->
                                summary.contains("바다")
                );
    }

    private Memory memory(
            String quote,
            MemoryFact.Kind kind,
            String subject,
            String relation,
            String value,
            int importance,
            LocalDateTime rememberedAt
    ) {
        Character character =
                new Character("Millie");

        Conversation conversation =
                Conversation.fresh(
                        character,
                        rememberedAt
                );

        ChatMessage source =
                new ChatMessage(
                        conversation,
                        character,
                        ChatRole.USER,
                        quote,
                        ContentMode.NORMAL,
                        rememberedAt
                );

        return Memory.fromChat(
                conversation,
                source,
                quote,
                importance,
                rememberedAt,
                "scenario-"
                        + quote.hashCode()
                        + "-"
                        + rememberedAt,
                new MemoryFact(
                        kind,
                        subject,
                        relation,
                        value
                )
        );
    }

    private Memory worldMemory(
            String summary,
            int importance,
            LocalDateTime rememberedAt
    ) {
        Character character =
                new Character("Millie");

        return new Memory(
                character,
                null,
                summary,
                importance,
                importance * 10,
                rememberedAt
        );
    }
}