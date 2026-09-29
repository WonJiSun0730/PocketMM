package com.example.projectmm.application.chat;

import com.example.projectmm.application.ai.*;
import com.example.projectmm.application.memory.*;
import com.example.projectmm.application.relationship.*;
import com.example.projectmm.application.simulation.*;
import com.example.projectmm.application.state.CustomStateProposalService;
import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.event.EventRepository;
import com.example.projectmm.domain.memory.*;
import com.example.projectmm.domain.player.PlayerProfile;
import com.example.projectmm.domain.relationship.*;
import com.example.projectmm.domain.scene.SceneRepository;
import com.example.projectmm.domain.simulation.StateDelta;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({com.example.projectmm.presentation.chat.ChatController.class, ChatService.class,
        com.example.projectmm.application.policy.ContentModePolicy.class,
        NarrativeOrchestrator.class, ConversationProgressService.class, CustomStateProposalService.class,
        CatchUpExecutionService.class, CatchUpOrchestrator.class, CatchUpSimulationService.class,
        CatchUpSegmentService.class, CatchUpEventCandidateService.class, EventCandidateValidator.class,
        AiContextBuilder.class, AiDecisionValidator.class, CatchUpSceneResolver.class, CatchUpSceneFactory.class,
        ResolvedEventPersistenceService.class, ResolvedEventMapper.class, RelationshipChangeService.class,
        RelationshipFactService.class, MemoryAccessService.class, MemoryLifecycleService.class,
        MemoryCreationService.class, MemoryPolicy.class, MemoryDetailService.class})
class ConversationCatchUpExecutionTest {
    @Autowired com.example.projectmm.presentation.chat.ChatController controller;
    @MockitoBean ConversationStartService starts;
    @MockitoBean com.example.projectmm.application.player.ConversationPlayerService players;
    @MockitoBean com.example.projectmm.application.dialogue.DialogueContextBuilder dialogueContexts;
    @MockitoBean com.example.projectmm.application.dialogue.DialogueGateway dialogueGateway;
    @MockitoBean com.example.projectmm.application.time.TimeInterpreter timeInterpreter;
    @MockitoBean ChatMemoryService chatMemory;
    @Autowired NarrativeOrchestrator narrative;
    @Autowired ConversationProgressService progress;
    @Autowired ConversationRepository rooms;
    @Autowired ConversationCatchUpRepository reports;
    @Autowired EventRepository events;
    @Autowired SceneRepository scenes;
    @Autowired MemoryRepository memories;
    @Autowired MemoryAccessStateRepository access;
    @Autowired RelationshipRepository relationships;
    @Autowired RelationshipFactRepository facts;
    @Autowired ChatMessageRepository messages;
    @Autowired EntityManager em;
    @MockitoBean PresenceClock clock;
    @MockitoBean AiGateway gateway;
    @MockitoBean MemoryRecallService recall;
    @MockitoBean MemoryReactivationContextService reactivation;
    @MockitoSpyBean CatchUpEventCandidateService candidates;
    private final LocalDateTime real = LocalDateTime.of(2026, 9, 29, 12, 0);
    private final LocalDateTime story = LocalDateTime.of(2030, 1, 1, 23, 0);

    @Test
    void httpChatReachesAdvancedPersistenceBeforeLiveReply() throws Exception {
        var room = fixture(70, 10);
        when(gateway.decide(any())).thenReturn(new AiDecision("REFLECTION", "잠시 감정을 돌아보았다.", 4,
                new StateDelta(0, 5), null, RelationshipDelta.none()));
        var context = mock(com.example.projectmm.domain.dialogue.DialogueContext.class);
        when(context.simulatedAt()).thenReturn(story.plusMinutes(2));
        when(dialogueContexts.build(anyLong(), anyLong(), any(), anyString())).thenAnswer(call -> {
            assertThat(events.count()).isEqualTo(1);
            assertThat(rooms.findById(room.getId()).orElseThrow().getMood()).isEqualTo(15);
            return context;
        });
        when(dialogueGateway.generateTurn(any(), anyString())).thenReturn(new com.example.projectmm.domain.dialogue.DialogueTurn(
                "이제 대화를 이어가자.", com.example.projectmm.domain.dialogue.DialogueStateChange.none()));
        absent(room);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/chat/" + room.getCharacter().getId())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"message\":\"\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        em.flush(); em.clear();
        assertThat(rooms.findById(room.getId()).orElseThrow().getChatOperationId()).isNull();
        assertThat(events.count()).isEqualTo(1);
        assertThat(reports.count()).isEqualTo(1);
        verify(gateway, times(1)).decide(any());
        verify(dialogueGateway, times(1)).generateTurn(any(), anyString());
    }

    @Test
    void runtimeMixedSegmentPersistsOnceAndKeepsRoomTokenAndEntryContracts() {
        var room = fixture(20, 10);
        var otherRoom = Conversation.fresh(room.getCharacter(), story);
        em.persist(otherRoom);
        var other = new Character("친구");
        em.persist(other);
        em.persist(new Relationship(room.getCharacter(), other));
        var ownMemory = Memory.fromAutomaticAction(room, null, "own", 5, story.minusDays(1));
        var foreignMemory = Memory.fromAutomaticAction(otherRoom, null, "foreign", 5, story.minusDays(1));
        var worldMemory = new Memory(room.getCharacter(), null, "world", 5, 50, story.minusDays(1));
        em.persist(ownMemory); em.persist(foreignMemory); em.persist(worldMemory); em.flush();
        when(gateway.decide(any())).thenReturn(new AiDecision("RECOVER", "친구와 이야기하며 진정했다.", 5, 3,
                new StateDelta(-3, 8), other.getId(), new RelationshipDelta(2, 3, 0),
                new MemoryAccessChange("ALL", "BLOCKED", null), List.of(),
                new RelationshipFactChange(other.getId(), "role", "friend")));
        absent(room);
        var operation = narrative.beginLiveDialogue(room.getCharacter().getId(), room.getId());
        assertThat(operation.entry()).isEqualTo(NarrativeEntry.AFTER_CATCH_UP);
        assertThat(operation.mode()).isEqualTo(NarrativeMode.LIVE_DIALOGUE);
        assertThat(operation.room().getId()).isEqualTo(room.getId());
        assertThat(operation.token()).isNotBlank();
        em.flush(); em.clear();
        var stored = rooms.findById(room.getId()).orElseThrow();
        assertThat(stored.getEnergy()).isEqualTo(47); // REST +30, AI -3, each once
        assertThat(stored.getMood()).isEqualTo(18);
        assertThat(stored.getSimulatedAt()).isEqualTo(story.plusMinutes(2));
        assertThat(stored.getChatOperationId()).isEqualTo(operation.token());
        assertThat(stored.getProgressPauseReason()).isNull();
        assertThat(events.count()).isEqualTo(2);
        assertThat(scenes.count()).isEqualTo(1);
        assertThat(scenes.findAll())
                .singleElement()
                .satisfies(scene ->
                        assertThat(scene.getConversation().getId())
                                .isEqualTo(room.getId())
                );
        assertThat(facts.findByFromCharacterId(room.getCharacter().getId())).hasSize(1);
        assertThat(relationships.findByFromCharacterIdAndToCharacterId(room.getCharacter().getId(), other.getId())
                .orElseThrow().getTrust()).isEqualTo(3);
        assertThat(memories.findAll().stream().filter(m -> m.getSourceEvent() != null).toList())
                .hasSize(1).allSatisfy(m -> assertThat(m.getConversation().getId()).isEqualTo(room.getId()));
        assertThat(access.findByMemoryId(ownMemory.getId()).orElseThrow().getAccessLevel()).isEqualTo(MemoryAccessLevel.BLOCKED);
        assertThat(access.findByMemoryId(foreignMemory.getId())).isEmpty();
        assertThat(access.findByMemoryId(worldMemory.getId())).isEmpty();
        assertThat(rooms.findById(otherRoom.getId()).orElseThrow().getEnergy()).isEqualTo(70);
        assertThat(em.createQuery("select s from CharacterState s", CharacterState.class).getSingleResult().getEnergy()).isEqualTo(20);
        assertThat(reports.findTopByConversationIdOrderByIdDesc(room.getId()).orElseThrow().getAppliedSeconds()).isEqualTo(120);
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(room.getId())).hasSize(2);
        progress.endChat(room.getCharacter().getId(), room.getId(), operation.token());
        assertThat(narrative.beginLiveDialogue(room.getCharacter().getId(), room.getId()).entry()).isEqualTo(NarrativeEntry.DIRECT);
        assertThat(events.count()).isEqualTo(2);
        assertThat(reports.count()).isEqualTo(1);
        verify(gateway, times(1)).decide(any());
        verify(candidates, times(1)).generate(any(), any());
        verify(recall).recallForConversation(eq(room.getCharacter().getId()), eq(room.getId()), any(), eq(5), anyString(), eq(false));
    }

    @Test
    void directDoesNotEnterAdvancedExecution() {
        var room = fixture(70, 70);
        var operation = narrative.beginLiveDialogue(room.getCharacter().getId(), room.getId());
        assertThat(operation.entry()).isEqualTo(NarrativeEntry.DIRECT);
        assertThat(operation.room().getSimulatedAt()).isEqualTo(story);
        assertThat(events.count()).isZero();
        verifyNoInteractions(gateway);
        verify(candidates, never()).generate(any(), any());
    }

    @Test
    void simpleRestStillRunsOnceWithoutAdvancedEvents() {
        var room = fixture(20, 70);
        absent(room);
        var operation = narrative.beginLiveDialogue(room.getCharacter().getId(), room.getId());
        assertThat(operation.room().getEnergy()).isEqualTo(50);
        assertThat(operation.entry()).isEqualTo(NarrativeEntry.AFTER_CATCH_UP);
        assertThat(memories.findForConversation(room.getCharacter().getId(), room.getId())).hasSize(1);
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(room.getId())).hasSize(1);
        assertThat(events.count()).isZero();
        verifyNoInteractions(gateway);
    }

    @Test
    void aiFailureRetriesOnceThenPersistsExistingRuleFallback() {
        var room = fixture(70, 10);
        when(gateway.decide(any())).thenThrow(new IllegalStateException("fake unavailable"));
        absent(room);
        var operation = narrative.beginLiveDialogue(room.getCharacter().getId(), room.getId());
        assertThat(operation.entry()).isEqualTo(NarrativeEntry.AFTER_CATCH_UP);
        assertThat(operation.room().getEnergy()).isEqualTo(70);
        assertThat(events.findAll()).hasSize(1).allSatisfy(e -> assertThat(e.getEventType()).isEqualTo("MOOD_CHANGE"));
        verify(gateway, times(2)).decide(any());
    }

    @Test
    void rejectedRoomAbilityChangeNeverMutatesSharedStateOrSavesEvent() {
        var room = fixture(70, 10);
        when(gateway.decide(any())).thenReturn(new AiDecision("X", "rejected", 5,
                new StateDelta(-2, 2, -5), null, RelationshipDelta.none()));
        absent(room);
        var operation = narrative.beginLiveDialogue(room.getCharacter().getId(), room.getId());
        assertThat(operation.room().getEnergy()).isEqualTo(70);
        assertThat(operation.room().getSimulatedAt()).isEqualTo(story.plusMinutes(2));
        assertThat(events.count()).isZero();
        verify(gateway, times(2)).decide(any());
    }

    private Conversation fixture(int energy, int mood) {
        when(clock.now()).thenReturn(real);
        var actor = new Character("NPC");
        var profile = new PlayerProfile(1, "Player", "설정", null);
        em.persist(actor); em.persist(profile);
        em.persist(new CharacterState(actor, energy, mood, story));
        var room = Conversation.fresh(actor, story);
        room.selectPlayerProfile(profile);
        room.applyRoomState(energy, mood, story);
        em.persist(room); em.flush();
        return room;
    }

    private void absent(Conversation room) {
        progress.configure(room.getCharacter().getId(), room.getId(), true);
        when(clock.now()).thenReturn(real.plusMinutes(2));
    }
}
