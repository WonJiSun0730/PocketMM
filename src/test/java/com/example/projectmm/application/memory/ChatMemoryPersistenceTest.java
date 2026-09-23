package com.example.projectmm.application.memory;

import com.example.projectmm.application.dialogue.DialogueContextBuilder;
import com.example.projectmm.application.chat.ConversationStartService;
import com.example.projectmm.application.player.*;
import com.example.projectmm.infrastructure.dialogue.GeminiDialogueGateway;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.policy.ContentModePolicy;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterProfile;
import com.example.projectmm.domain.relationship.Relationship;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.DialogueMemoryContext;
import com.example.projectmm.domain.memory.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({ChatMemoryWriter.class, MemoryRecallService.class, MemoryDecayPolicy.class,
        MemoryCompressionService.class, RuleBasedMemoryCompressor.class,
        DialogueContextBuilder.class, ContentModePolicy.class, ConversationStartService.class,
        PlayerProfileService.class, ConversationPlayerService.class})
class ChatMemoryPersistenceTest {
    @Autowired EntityManager em;
    @Autowired ChatMemoryWriter writer;
    @Autowired MemoryRepository memories;
    @Autowired ChatMessageRepository messages;
    @Autowired DialogueContextBuilder contexts;
    @Autowired ConversationStartService starts;
    @Autowired ConversationRepository conversations;
    @Autowired PlayerProfileService playerProfiles;
    @Autowired ConversationPlayerService players;
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 23, 12, 0);

    @Test
    void playerSelectionPersistsPerRoomAndOnlyTextReachesDialogueRequest() {
        Character character = new Character("상대 캐릭터");
        em.persist(character);
        em.persist(new CharacterState(character, 70, 70, now));
        String avatar = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j9v0AAAAASUVORK5CYII=";
        var firstProfile = playerProfiles.save(null, new PlayerProfileRequest("하린", "검은 단발머리의 도서관 사서. 존댓말을 쓴다.", avatar));
        var secondProfile = playerProfiles.save(null, new PlayerProfileRequest("서윤", "은발의 여행가. 지도를 들고 다닌다.", null));
        var roomOne = starts.start(character.getId(), firstProfile.id());
        var roomTwo = starts.start(character.getId(), secondProfile.id());
        em.flush();
        em.clear();
        assertThat(players.current(character.getId()).playerProfile().id()).isEqualTo(secondProfile.id());
        var context = contexts.build(character.getId(), roomOne.conversationId(), ContentMode.NORMAL, "안녕하세요.");
        assertThat(context.playerProfile().name()).isEqualTo("하린");
        assertThat(context.playerProfile().description()).contains("도서관 사서");
        // 실제 HTTP 호출 없이 최종 Gemini 요청에 텍스트만 포함되는지 확인한다.
        var gateway = new GeminiDialogueGateway("test", "test", new ObjectMapper());
        Object request = ReflectionTestUtils.invokeMethod(gateway, "createRequest", context, "안녕하세요.");
        String json = new ObjectMapper().writeValueAsString(request);
        assertThat(json).contains("하린", "도서관 사서").doesNotContain("data:image", "iVBORw0KGgo");

        players.select(character.getId(), roomOne.conversationId(), secondProfile.id());
        em.flush();
        em.clear();
        assertThat(contexts.build(character.getId(), roomOne.conversationId(), ContentMode.NORMAL).playerProfile().name()).isEqualTo("서윤");
        assertThat(conversations.findById(roomTwo.conversationId()).orElseThrow().getPlayerProfile().getId()).isEqualTo(secondProfile.id());
        playerProfiles.save(secondProfile.id(), new PlayerProfileRequest("서윤", "은발의 여행가. 파란 외투를 입는다.", null));
        assertThat(contexts.build(character.getId(), roomOne.conversationId(), ContentMode.NORMAL).playerProfile().description()).contains("파란 외투");
    }

    @Test
    void playerProfilesEnforceFiveHundredCharactersAndTenSlots() {
        // 이름 1자 + 설정 499자는 허용하고 501자는 거절한다.
        var profile = playerProfiles.save(null, new PlayerProfileRequest("가", "나".repeat(499), null));
        for (int i = 1; i < 10; i++) playerProfiles.save(null, new PlayerProfileRequest("인물" + i, "설정", null));
        assertThat(playerProfiles.list()).hasSize(10);
        assertThat(playerProfiles.save(profile.id(), new PlayerProfileRequest("수정", "설정 수정", null)).name()).isEqualTo("수정");
        assertThatThrownBy(() -> playerProfiles.save(null, new PlayerProfileRequest("초과", "설정", null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> playerProfiles.save(profile.id(), new PlayerProfileRequest("가", "나".repeat(500), null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> playerProfiles.save(profile.id(), new PlayerProfileRequest("가", "설정", "https://example.com/avatar.png")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void selectingProfileCannotModifyAnotherCharactersRoom() {
        Character first = new Character("첫 캐릭터");
        Character second = new Character("다른 캐릭터");
        em.persist(first);
        em.persist(second);
        var profile = playerProfiles.save(null, new PlayerProfileRequest("하린", "사서", null));
        var room = starts.start(first.getId(), profile.id());
        assertThatThrownBy(() -> players.select(second.getId(), room.conversationId(), profile.id()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void freshStartPreservesOldRoomAndUsesOnlyItsOwnIntroStateAndMemories() {
        Character character = new Character("새 대화 QA");
        Character other = new Character("관계 대상");
        em.persist(character);
        em.persist(other);
        var state = new CharacterState(character, 12, 23, now);
        em.persist(state);
        em.persist(new Relationship(character, other));
        em.persist(new CharacterProfile(character, "소개", "캐릭터 설정", null, "*첫 만남이다.*",
                "안녕하세요.", null, null, null, null));
        var old = new Conversation(character, now.plusDays(10));
        em.persist(old);
        var source = message(old, "내 반려묘 이름은 감자야.", now);
        em.persist(source);
        writer.save(old, source, source.getContent(), List.of(new ChatMemoryCandidate(source.getContent(), 5)), now);
        em.persist(new Memory(character, null, "옛 세계 사건", 8, 80, now));
        // 저장 시각과 같은 마이크로초 정밀도로 비교해 나노초 절삭에 따른 오탐을 피한다.
        var before = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var fresh = starts.start(character.getId());
        em.flush();
        em.clear();

        assertThat(fresh.simulatedAt()).isBetween(before, LocalDateTime.now());
        assertThat(fresh.conversationId()).isNotEqualTo(old.getId());
        assertThat(fresh.messages()).hasSize(1);
        assertThat(fresh.messages().getFirst().message()).isEqualTo("*첫 만남이다.*");
        assertThat(conversations.findTopByCharacterIdOrderByCreatedAtDescIdDesc(character.getId()).orElseThrow().getId())
                .isEqualTo(fresh.conversationId());
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(old.getId())).hasSize(1);
        assertThat(memories.findForConversation(character.getId(), old.getId())).hasSize(2);
        var context = contexts.build(character.getId(), fresh.conversationId(), ContentMode.NORMAL, "감자 기억해?");
        assertThat(context.memories()).isEmpty();
        assertThat(context.relationships()).isEmpty();
        assertThat(context.energy()).isEqualTo(70);
        assertThat(context.mood()).isEqualTo(70);
        assertThat(context.characterProfile().rawDescription()).isEqualTo("캐릭터 설정");
        assertThat(context.recentMessages()).hasSize(1);
        assertThat(em.find(CharacterState.class, state.getId()).getEnergy()).isEqualTo(12);

        // 분리된 방에서도 새 발언 저장·회상은 계속 동작해야 한다.
        var newRoom = conversations.findById(fresh.conversationId()).orElseThrow();
        var newSource = message(newRoom, "오늘부터 반려묘 이름은 양파야.", fresh.simulatedAt());
        em.persist(newSource);
        writer.save(newRoom, newSource, newSource.getContent(), List.of(new ChatMemoryCandidate(newSource.getContent(), 5)), fresh.simulatedAt());
        assertThat(contexts.build(character.getId(), fresh.conversationId(), ContentMode.NORMAL, "반려묘 이름은?").memories())
                .extracting(DialogueMemoryContext::summary).containsExactly("사용자가 캐릭터에게 말한 내용: \"오늘부터 반려묘 이름은 양파야.\"");
    }

    @Test
    void freshStartWithoutIntroIsEmptyAndMissingCharacterDoesNotCreateRoom() {
        Character character = new Character("인트로 없음");
        em.persist(character);
        var first = starts.start(character.getId());
        var second = starts.start(character.getId());
        assertThat(first.messages()).isEmpty();
        assertThat(second.messages()).isEmpty();
        assertThat(second.conversationId()).isNotEqualTo(first.conversationId());
        assertThat(conversations.findByCharacterIdOrderByCreatedAtDesc(character.getId())).hasSize(2);
        assertThatThrownBy(() -> starts.start(-1L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void persistsGroundedMemoryAndRecallsItAfterSourceLeavesRecentTwentyMessages() {
        Character character = new Character("Memory QA");
        em.persist(character);
        em.persist(new CharacterState(character, 80, 70, now));
        Conversation conversation = new Conversation(character, now);
        em.persist(conversation);
        ChatMessage source = message(conversation, "내 반려묘 이름은 감자야.", now.minusHours(1));
        em.persist(source);
        var candidates = List.of(new ChatMemoryCandidate(source.getContent(), 5));
        assertThat(writer.save(conversation, source, source.getContent(), candidates, now).saved()).hasSize(1);
        assertThat(writer.save(conversation, source, source.getContent(), candidates, now).saved()).isEmpty();
        for (int i = 0; i < 22; i++) em.persist(message(conversation, "일반 대화 " + i, now.minusMinutes(30).plusSeconds(i)));
        em.flush();
        em.clear();

        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(conversation.getId()))
                .extracting(ChatMessage::getContent).doesNotContain(source.getContent());
        var context = contexts.build(character.getId(), conversation.getId(), ContentMode.NORMAL, "반려묘 이름 기억해?");
        assertThat(context.memories()).extracting(DialogueMemoryContext::summary)
                .containsExactly("사용자가 캐릭터에게 말한 내용: \"내 반려묘 이름은 감자야.\"");
        assertThat(memories.findForConversation(character.getId(), conversation.getId())).hasSize(1);
    }

    @Test
    void querySeparatesConversationsAndCharactersButRetainsWorldMemories() {
        Character character = new Character("C");
        Character other = new Character("D");
        em.persist(character);
        em.persist(other);
        Conversation first = new Conversation(character, now);
        Conversation second = new Conversation(character, now);
        Conversation otherConversation = new Conversation(other, now);
        em.persist(first);
        em.persist(second);
        em.persist(otherConversation);
        for (Conversation conversation : List.of(first, second, otherConversation)) {
            ChatMessage source = message(conversation, "내 반려묘 이름은 감자야.", now);
            em.persist(source);
            writer.save(conversation, source, source.getContent(), List.of(new ChatMemoryCandidate(source.getContent(), 5)), now);
        }
        em.persist(new Memory(character, null, "공통 세계 사건", 6, 60, now));
        em.flush();
        em.clear();
        var found = memories.findForConversation(character.getId(), first.getId());
        assertThat(found).hasSize(2);
        assertThat(found).filteredOn(m -> m.getConversation() != null)
                .allSatisfy(m -> assertThat(m.getConversation().getId()).isEqualTo(first.getId()));
        assertThat(found).extracting(Memory::getSummary).contains("공통 세계 사건");
        assertThat(memories.findForConversation(other.getId(), otherConversation.getId())).hasSize(1);
    }

    private ChatMessage message(Conversation conversation, String text, LocalDateTime at) {
        return new ChatMessage(conversation, conversation.getCharacter(), ChatRole.USER, text, ContentMode.NORMAL, at);
    }
}
