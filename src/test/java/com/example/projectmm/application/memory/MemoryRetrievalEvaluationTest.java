package com.example.projectmm.application.memory;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryRetrievalEvaluationTest {

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

    private final MemoryRecallService memoryRecallService =
            new MemoryRecallService(
                    memoryRepository,
                    new MemoryDecayPolicy(),
                    memoryCompressionService,
                    characterStateRepository,
                    memoryAccessStateRepository,
                    memoryLifecycleService
            );

    private final LocalDateTime now =
            LocalDateTime.of(
                    2026, 9, 28,
                    12, 0
            );

    @Test
    void 평가_구체적인_내용을_질문하면_해당_기억을_검색한다() {

        Memory target =
                memory(
                        "나는 오리고기를 제일 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "오리고기",
                        5,
                        now.minusDays(5)
                );

        Memory distractor =
                memory(
                        "나는 제주도에서 지갑을 잃어버렸어",
                        MemoryFact.Kind.REPORTED_EVENT,
                        "나",
                        "잃어버렸어",
                        "지갑",
                        9,
                        now.minusDays(1)
                );

        stubMemories(
                distractor,
                target
        );

        assertTopMemoryContains(
                "내가 좋아한다고 했던 음식이 뭐였지?",
                "오리고기"
        );
    }

    @Test
    void 평가_직접_키워드가_없어도_기억_종류를_이용해_검색한다() {

        Memory target =
                memory(
                        "나는 오리고기를 제일 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "오리고기",
                        5,
                        now.minusDays(5)
                );

        Memory distractor =
                memory(
                        "나는 제주도에서 지갑을 잃어버렸어",
                        MemoryFact.Kind.REPORTED_EVENT,
                        "나",
                        "잃어버렸어",
                        "지갑",
                        9,
                        now.minusDays(1)
                );

        stubMemories(
                distractor,
                target
        );

        assertTopMemoryContains(
                "내 음식 취향 기억나?",
                "오리고기"
        );
    }

    @Test
    void 평가_사건_질문에는_선호보다_관련_사건을_검색한다() {

        Memory target =
                memory(
                        "나는 제주도에서 지갑을 잃어버렸어",
                        MemoryFact.Kind.REPORTED_EVENT,
                        "나",
                        "잃어버렸어",
                        "지갑",
                        5,
                        now.minusDays(5)
                );

        Memory distractor =
                memory(
                        "나는 오리고기를 제일 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "오리고기",
                        9,
                        now.minusDays(1)
                );

        stubMemories(
                distractor,
                target
        );

        assertTopMemoryContains(
                "제주도에서 무슨 일이 있었지?",
                "지갑"
        );
    }

    @Test
    void 평가_미래_시점의_기억은_검색하지_않는다() {

        Memory valid =
                memory(
                        "나는 오리고기를 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "오리고기",
                        5,
                        now.minusDays(1)
                );

        Memory future =
                memory(
                        "나는 연어를 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "연어",
                        9,
                        now.plusDays(1)
                );

        stubMemories(
                future,
                valid
        );

        List<EffectiveMemory> result =
                recall(
                        "내가 좋아하는 음식은?"
                );

        assertThat(result)
                .extracting(EffectiveMemory::summary)
                .noneMatch(
                        summary ->
                                summary.contains("연어")
                );
    }

    @Test
    void 평가_top_k에_정답_기억이_포함되는지_측정한다() {

        Memory preference =
                memory(
                        "나는 오리고기를 제일 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "오리고기",
                        5,
                        now.minusDays(5)
                );

        Memory travel =
                memory(
                        "나는 제주도에서 지갑을 잃어버렸어",
                        MemoryFact.Kind.REPORTED_EVENT,
                        "나",
                        "잃어버렸어",
                        "지갑",
                        7,
                        now.minusDays(4)
                );

        Memory plan =
                memory(
                        "나는 다음 달에 부산에 갈 예정이야",
                        MemoryFact.Kind.PLAN,
                        "나",
                        "갈 예정이야",
                        "부산",
                        6,
                        now.minusDays(3)
                );

        Memory constraint =
                memory(
                        "나는 땅콩을 먹으면 안 돼",
                        MemoryFact.Kind.CONSTRAINT,
                        "나",
                        "먹으면 안 돼",
                        "땅콩",
                        8,
                        now.minusDays(2)
                );

        stubMemories(
                constraint,
                plan,
                travel,
                preference
        );

        List<RetrievalCase> cases =
                List.of(
                        new RetrievalCase(
                                "내 음식 취향 기억나?",
                                "오리고기"
                        ),
                        new RetrievalCase(
                                "제주도에서 무슨 일이 있었지?",
                                "지갑"
                        ),
                        new RetrievalCase(
                                "다음 달 계획이 뭐였지?",
                                "부산"
                        ),
                        new RetrievalCase(
                                "내가 먹으면 안 되는 게 뭐였지?",
                                "땅콩"
                        )
                );

        RetrievalScore top1 =
                evaluate(
                        cases,
                        1
                );

        RetrievalScore top3 =
                evaluate(
                        cases,
                        3
                );

        assertThat(top1.recall())
                .as(
                        "Recall@1 (%d/%d)",
                        top1.hits(),
                        top1.total()
                )
                .isEqualTo(1.0);

        assertThat(top3.recall())
                .as(
                        "Recall@3 (%d/%d)",
                        top3.hits(),
                        top3.total()
                )
                .isEqualTo(1.0);
    }

    @Test
    void 평가_소멸한_기억은_관련_질문이어도_검색하지_않는다() {

        Memory fadedTarget =
                memory(
                        "나는 오리고기를 제일 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "오리고기",
                        1,
                        now.minusDays(365)
                );

        Memory survivingDistractor =
                memory(
                        "나는 제주도에서 지갑을 잃어버렸어",
                        MemoryFact.Kind.REPORTED_EVENT,
                        "나",
                        "잃어버렸어",
                        "지갑",
                        9,
                        now.minusDays(1)
                );

        stubMemories(
                fadedTarget,
                survivingDistractor
        );

        List<EffectiveMemory> result =
                recall(
                        "내 음식 취향 기억나?"
                );

        assertThat(result)
                .extracting(EffectiveMemory::summary)
                .noneMatch(
                        summary ->
                                summary.contains("오리고기")
                );
    }

    @Test
    void 평가_독립_시작에서는_공유_기억을_검색하지_않는다() {

        Memory conversationMemory =
                memory(
                        "나는 오리고기를 제일 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "오리고기",
                        5,
                        now.minusDays(3)
                );

        Memory sharedMemory =
                worldMemory(
                        "과거 세계에서 함께 바다를 보았다",
                        9,
                        now.minusDays(2)
                );

        stubMemories(
                sharedMemory,
                conversationMemory
        );

        List<EffectiveMemory> result =
                memoryRecallService.recallForConversation(
                        1L,
                        10L,
                        now,
                        10,
                        "과거에 함께 뭘 했지?",
                        false
                );

        assertThat(result)
                .extracting(EffectiveMemory::summary)
                .noneMatch(
                        summary ->
                                summary.contains("바다")
                );

        assertThat(result)
                .extracting(EffectiveMemory::summary)
                .anyMatch(
                        summary ->
                                summary.contains("오리고기")
                );
    }

    @Test
    void 평가_기억력이_저하되면_약한_기억은_제외하고_강한_기억은_회상한다() {

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
                        1L
                )
        ).thenReturn(
                java.util.Optional.of(state)
        );

        Memory weakTarget =
                memory(
                        "나는 오리고기를 제일 좋아해",
                        MemoryFact.Kind.PREFERENCE,
                        "나",
                        "좋아해",
                        "오리고기",
                        5,
                        now.minusDays(10)
                );

        Memory strongMemory =
                memory(
                        "나는 제주도에서 지갑을 잃어버렸어",
                        MemoryFact.Kind.REPORTED_EVENT,
                        "나",
                        "잃어버렸어",
                        "지갑",
                        9,
                        now.minusDays(1)
                );

        stubMemories(
                weakTarget,
                strongMemory
        );

        List<EffectiveMemory> result =
                recall(
                        "내 음식 취향 기억나?"
                );

        assertThat(result)
                .extracting(EffectiveMemory::summary)
                .noneMatch(
                        summary ->
                                summary.contains("오리고기")
                );

        assertThat(result)
                .extracting(EffectiveMemory::summary)
                .anyMatch(
                        summary ->
                                summary.contains("지갑")
                );
    }

    private void stubMemories(
            Memory... memories
    ) {
        when(
                memoryRepository.findForConversation(
                        1L,
                        10L
                )
        ).thenReturn(
                List.of(memories)
        );
    }

    private List<EffectiveMemory> recall(
            String query
    ) {
        return memoryRecallService.recallForConversation(
                1L,
                10L,
                now,
                10,
                query
        );
    }

    private void assertTopMemoryContains(
            String query,
            String expected
    ) {
        List<EffectiveMemory> result =
                recall(query);

        assertThat(result)
                .isNotEmpty();

        assertThat(result.get(0).summary())
                .contains(expected);
    }

    private boolean isTopKHit(
            RetrievalCase testCase,
            int k
    ) {
        List<EffectiveMemory> result =
                memoryRecallService.recallForConversation(
                        1L,
                        10L,
                        now,
                        k,
                        testCase.query()
                );

        return result.stream()
                .anyMatch(
                        memory ->
                                memory.summary()
                                        .contains(
                                                testCase.expected()
                                        )
                );
    }

    private RetrievalScore evaluate(
            List<RetrievalCase> cases,
            int k
    ) {
        int hits = 0;

        for (RetrievalCase testCase : cases) {
            if (isTopKHit(testCase, k)) {
                hits++;
            }
        }

        return new RetrievalScore(
                hits,
                cases.size()
        );
    }

    private record RetrievalScore(
            int hits,
            int total
    ) {
        double recall() {
            if (total == 0) {
                return 0.0;
            }

            return (double) hits / total;
        }
    }

    private record RetrievalCase(
            String query,
            String expected
    ) {
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
                "eval-"
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
}