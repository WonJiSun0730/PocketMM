package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.memory.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.application.policy.ContentMode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StructuredMemoryTest {
    private final LocalDateTime at = LocalDateTime.of(2030, 1, 1, 12, 0);

    @Test void structuresMustBeGroundedAndCannotReplaceEvidenceAfterCompression() {
        String quote = "나는 복숭아를 먹을 수 없어.";
        var fact = new MemoryFact(MemoryFact.Kind.CONSTRAINT, "나는", "먹을 수 없어", "복숭아");
        assertThat(fact.groundedIn(quote)).isTrue();
        for (var invalid : List.of(new MemoryFact(null, "나는", "먹을 수 없어", "복숭아"),
                new MemoryFact(MemoryFact.Kind.CONSTRAINT, "타인", "먹을 수 없어", "복숭아"),
                new MemoryFact(MemoryFact.Kind.CONSTRAINT, "나는", "좋아해", "복숭아"),
                new MemoryFact(MemoryFact.Kind.CONSTRAINT, "나는", "먹을 수 없어", "사과"))) {
            assertThat(invalid.groundedIn(quote)).isFalse();
        }
        var room = new Conversation(new Character("NPC"), at);
        var memory = Memory.fromChat(room, null, quote, 6, at, "key", fact);
        memory.compress("요약");
        assertThat(memory.getEvidenceQuote()).isEqualTo(quote);
        assertThat(memory.getFact().getRelation()).isEqualTo("먹을 수 없어");
    }

    @Test void writerCannotBeTrickedIntoSavingNarrationAsSpeechOrUngroundedStructure() {
        var repo = mock(MemoryRepository.class);
        var rooms = mock(ConversationRepository.class);
        var writer = new ChatMemoryWriter(repo, rooms);
        var character = new Character("NPC");
        var room = new Conversation(character, at);
        ReflectionTestUtils.setField(room, "id", 1L);
        var source = new ChatMessage(room, character, ChatRole.USER, "*비밀 이름은 감자다.* 안녕", ContentMode.NORMAL, at);
        ReflectionTestUtils.setField(source, "id", 2L);
        assertThatThrownBy(() -> writer.save(room, source, source.getContent(),
                List.of(new ChatMemoryCandidate("비밀 이름은 감자다.", 5)), at)).isInstanceOf(IllegalArgumentException.class);
        when(rooms.lockForMemory(1L)).thenReturn(Optional.of(room));
        assertThat(writer.save(room, source, "안녕", List.of(new ChatMemoryCandidate("안녕", 5,
                new MemoryFact(MemoryFact.Kind.IDENTITY, "나", "이름", "감자"))), at).saved()).isEmpty();
        verifyNoInteractions(repo);
    }

    @Test void recallUsesStructuredSubjectEvenAfterSummaryCompressionAndKeepsLegacyFallback() {
        var repository = mock(MemoryRepository.class);
        var recall = new MemoryRecallService(
                repository,
                new MemoryDecayPolicy(),
                mock(MemoryCompressionService.class),
                mock(CharacterStateRepository.class),
                mock(MemoryAccessStateRepository.class),
                mock(MemoryLifecycleService.class)
        );
        var character = new Character("NPC");
        var room = new Conversation(character, at);
        var structured = Memory.fromChat(room, null, "내 반려묘 이름은 감자야.", 5, at, "key",
                new MemoryFact(MemoryFact.Kind.RELATIONSHIP, "반려묘", "이름", "감자"));
        structured.compress("반려동물 이야기");
        var legacy = new Memory(character, null, "세계 대전이 끝났다", 10, 100, at);
        when(repository.findForConversation(1L, 2L)).thenReturn(List.of(legacy, structured));
        var result = recall.recallForConversation(1L, 2L, at, 2, "반려묘 이름");
        assertThat(result.getFirst().evidenceQuote()).contains("감자");
        assertThat(result.getFirst().fact()).isNotNull();
        assertThat(result.getLast().fact()).isNull();
        assertThat(result.getLast().summary()).isEqualTo(legacy.getSummary());
    }

    @Test void structureDoesNotOutrankEquallyRelevantStrongerLegacyMemory() {
        var repository = mock(MemoryRepository.class);
        var recall = new MemoryRecallService(
                repository,
                new MemoryDecayPolicy(),
                mock(MemoryCompressionService.class),
                mock(CharacterStateRepository.class),
                mock(MemoryAccessStateRepository.class),
                mock(MemoryLifecycleService.class)
        );
        var character = new Character("NPC");
        var room = new Conversation(character, at);
        var structured = Memory.fromChat(room, null, "내 반려묘 이름은 감자야.", 5, at, "key",
                new MemoryFact(MemoryFact.Kind.RELATIONSHIP, "반려묘", "이름", "감자"));
        var legacy = new Memory(character, null, "내 반려묘 이름은 양파야.", 8, 80, at);
        when(repository.findForConversation(1L, 2L)).thenReturn(List.of(structured, legacy));
        assertThat(recall.recallForConversation(1L, 2L, at, 1, "반려묘 이름").getFirst().summary())
                .isEqualTo(legacy.getSummary());
    }

    @Test void structuredQuoteCannotDropNegationAttributionOrQuestionContext() {
        var repo = mock(MemoryRepository.class);
        var rooms = mock(ConversationRepository.class);
        var writer = new ChatMemoryWriter(repo, rooms);
        var character = new Character("NPC");
        var room = new Conversation(character, at);
        ReflectionTestUtils.setField(room, "id", 1L);
        when(rooms.lockForMemory(1L)).thenReturn(Optional.of(room));
        String quote = "나는 복숭아를 좋아해";
        var candidate = new ChatMemoryCandidate(quote, 5,
                new MemoryFact(MemoryFact.Kind.PREFERENCE, "나는", "좋아해", "복숭아"));
        for (String input : List.of(quote + " 본 적이 없어.", "만약 " + quote + "도 먹지는 않을 거야.",
                "친구가 '" + quote + "'라고 말했어.", quote + "?")) {
            var source = new ChatMessage(room, character, ChatRole.USER, input, ContentMode.NORMAL, at);
            ReflectionTestUtils.setField(source, "id", 2L);
            assertThat(writer.save(room, source, input, List.of(candidate), at).saved()).isEmpty();
            assertThat(writer.save(room, source, input,
                    List.of(new ChatMemoryCandidate(quote, 5)), at).saved()).isEmpty();
        }
        String question = quote + "?";
        var source = new ChatMessage(room, character, ChatRole.USER, question, ContentMode.NORMAL, at);
        ReflectionTestUtils.setField(source, "id", 2L);
        assertThat(writer.save(room, source, question, List.of(new ChatMemoryCandidate(question, 5, candidate.fact())), at)
                .saved()).isEmpty();
        verifyNoInteractions(repo);
    }
}
