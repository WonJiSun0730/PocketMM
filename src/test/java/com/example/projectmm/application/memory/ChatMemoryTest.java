package com.example.projectmm.application.memory;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatMemoryTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 23, 9, 0);
    private final Character character = new Character("C");
    private final Conversation conversation = conversation(1L);
    private final MemoryRepository memories = mock(MemoryRepository.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ChatMemoryWriter writer = new ChatMemoryWriter(memories, conversations);

    @Test
    void privateNarrationAndOocAreNotExtractionInput() {
        assertThat(SpokenText.from("*속으로만 보라색 우산을 생각했다.* 나는 복숭아 알레르기가 있어."))
                .isEqualTo("나는 복숭아 알레르기가 있어.");
        assertThat(SpokenText.from("*속으로만 생각했다.*")).isEmpty();
        assertThat(SpokenText.from("*닫히지 않은 비밀 생각")).isEmpty();
        assertThat(SpokenText.from("(OOC: 보라색 우산을 기억해)")).isEmpty();
    }

    @Test
    void savesOnlyGroundedQuotesAndKeepsSourceAndAttribution() {
        when(conversations.lockForMemory(1L)).thenReturn(Optional.of(conversation));
        ChatMessage source = source("나는 복숭아 알레르기가 있어. 내일 부산에 갈 생각이야.");
        var result = writer.save(conversation, source, source.getContent(), List.of(
                new ChatMemoryCandidate("나는 복숭아 알레르기가 있어.", 6),
                new ChatMemoryCandidate("이미 부산에 갔다.", 8),
                new ChatMemoryCandidate("내일 부산에 갈 생각이야.", 11)), now);
        assertThat(result.status()).isEqualTo("SAVED");
        var saved = ArgumentCaptor.forClass(Memory.class);
        verify(memories).save(saved.capture());
        assertThat(saved.getValue().getSourceChatMessage()).isSameAs(source);
        assertThat(saved.getValue().getConversation()).isSameAs(conversation);
        assertThat(saved.getValue().getSummary()).isEqualTo("사용자가 캐릭터에게 말한 내용: \"나는 복숭아 알레르기가 있어.\"");
        assertThat(saved.getValue().getChatFingerprint()).hasSize(64);
    }

    @Test
    void duplicateQuotesAndRetriesDoNotCreateExtraMemories() {
        when(conversations.lockForMemory(1L)).thenReturn(Optional.of(conversation));
        Set<String> savedKeys = new HashSet<>();
        when(memories.existsByChatFingerprint(anyString())).thenAnswer(a -> savedKeys.contains(a.getArgument(0)));
        when(memories.save(any(Memory.class))).thenAnswer(a -> {
            Memory m = a.getArgument(0);
            savedKeys.add(m.getChatFingerprint());
            return m;
        });
        ChatMessage source = source("내 반려묘 이름은 감자야.");
        var candidate = new ChatMemoryCandidate(source.getContent(), 5);
        assertThat(writer.save(conversation, source, source.getContent(), List.of(candidate, candidate), now).saved()).hasSize(1);
        assertThat(writer.save(conversation, source, source.getContent(), List.of(candidate), now).status()).isEqualTo("NONE");
        verify(memories, times(1)).save(any());
    }

    @Test
    void rejectsWrongSourceConversationAndLimitsCandidateCount() {
        ChatMessage source = source("하나. 둘. 셋. 넷.");
        assertThatThrownBy(() -> writer.save(conversation(2L), source, source.getContent(), List.of(), now))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(memories, conversations);
        when(conversations.lockForMemory(1L)).thenReturn(Optional.of(conversation));
        var quotes = List.of("하나.", "둘.", "셋.", "넷.").stream()
                .map(q -> new ChatMemoryCandidate(q, 5)).toList();
        assertThat(writer.save(conversation, source, source.getContent(), quotes, now).saved()).hasSize(3);
    }

    @Test
    void extractionFailureAndPrivateOnlyInputDoNotFailChat() {
        ChatMemoryExtractor extractor = mock(ChatMemoryExtractor.class);
        ChatMemoryWriter mockWriter = mock(ChatMemoryWriter.class);
        ChatMemoryService service = new ChatMemoryService(extractor, mockWriter);
        assertThat(service.remember(conversation, source("*비밀을 속으로 생각했다.*"), now).status()).isEqualTo("SKIPPED");
        verifyNoInteractions(extractor, mockWriter);
        when(extractor.extract(anyString())).thenThrow(new IllegalStateException("quota"));
        assertThat(service.remember(conversation, source("나는 복숭아 알레르기가 있어."), now).status()).isEqualTo("FAILED");
        verifyNoInteractions(mockWriter);
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.remember(conversation, source("나는 복숭아 알레르기가 있어."), now).status()).isEqualTo("DISABLED");
        verify(extractor, times(1)).extract(anyString());
    }

    @Test
    void recallRanksRelatedOlderMemoryAboveUnrelatedStrongMemoryAndExcludesFutureAndFaded() {
        var recall = new MemoryRecallService(
                memories,
                new MemoryDecayPolicy(),
                mock(MemoryCompressionService.class),
                mock(CharacterStateRepository.class)
        );
        Memory relevant = new Memory(character, null, "반려묘 이름은 감자", 5, 50, now.minusDays(2));
        Memory unrelated = new Memory(character, null, "세계 대전이 끝났다", 10, 100, now);
        Memory faded = new Memory(character, null, "반려묘 이름은 양파", 4, 40, now.minusDays(100));
        Memory future = new Memory(character, null, "반려묘 이름은 당근", 10, 100, now.plusDays(1));
        when(memories.findForConversation(7L, 1L)).thenReturn(List.of(unrelated, faded, future, relevant));
        assertThat(recall.recallForConversation(7L, 1L, now, 1, "반려묘 이름 기억해?"))
                .extracting(EffectiveMemory::summary).containsExactly("반려묘 이름은 감자");
        verify(memories).findForConversation(7L, 1L);
        verify(memories, never()).findByCharacterIdOrderByRememberedAtDesc(anyLong());
    }

    @Test
    void globalSimulationCannotRecallConversationMemories() {
        var recall = new MemoryRecallService(
                memories,
                new MemoryDecayPolicy(),
                mock(MemoryCompressionService.class),
                mock(CharacterStateRepository.class)
        );
        Memory chat = Memory.fromChat(conversation, source("내 반려묘 이름은 감자야."), "내 반려묘 이름은 감자야.", 5, now, "key");
        when(memories.findByCharacterIdOrderByRememberedAtDesc(7L)).thenReturn(List.of(chat));
        assertThat(recall.recall(7L, now, 5)).isEmpty();
    }

    private Conversation conversation(Long id) {
        var c = new Conversation(character, now);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private ChatMessage source(String text) {
        var message = new ChatMessage(conversation, character, ChatRole.USER, text, ContentMode.NORMAL, now);
        ReflectionTestUtils.setField(message, "id", 10L);
        return message;
    }
}
