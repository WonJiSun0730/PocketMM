package com.example.projectmm.application.dialogue;

import com.example.projectmm.application.chat.ConversationProgressService;
import com.example.projectmm.application.chat.NarrativeEntry;
import com.example.projectmm.application.chat.PresenceClock;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.policy.ContentModePolicy;
import com.example.projectmm.application.simulation.CatchUpEventCandidateService;
import com.example.projectmm.application.simulation.CatchUpSegmentService;
import com.example.projectmm.application.simulation.CatchUpSimulationService;
import com.example.projectmm.application.simulation.EventCandidateValidator;
import com.example.projectmm.application.state.CustomStateProposalService;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.dialogue.DialogueMessageContext;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.player.PlayerProfile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DataJpaTest(
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        },
        showSql = false
)
@Import({
        ConversationProgressService.class,
        com.example.projectmm.application.chat.BeginChatPreparationService.class,
        com.example.projectmm.application.chat.BeginChatCatchUpResolver.class,
        com.example.projectmm.application.chat.BeginChatFinalizationService.class,
        com.example.projectmm.application.simulation.CatchUpClaimService.class,
        CatchUpSimulationService.class,
        CatchUpSegmentService.class,
        CatchUpEventCandidateService.class,
        EventCandidateValidator.class,
        CustomStateProposalService.class,
        DialogueContextBuilder.class,
        ContentModePolicy.class,
        com.example.projectmm.application.relationship.RelationshipChangeService.class,
        com.example.projectmm.application.relationship.RelationshipFactService.class
})
class NarrativeContextIntegrationTest {
    @MockitoBean com.example.projectmm.application.simulation.CatchUpExecutionService execution;

    @Autowired
    EntityManager em;

    @Autowired
    ConversationProgressService progressService;

    @Autowired
    DialogueContextBuilder contextBuilder;

    @Autowired
    ConversationRepository conversations;

    @MockitoBean
    PresenceClock clock;

    @MockitoBean
    MemoryRecallService memoryRecallService;

    private final LocalDateTime real =
            LocalDateTime.of(
                    2026, 9, 28, 12, 0
            );

    private final LocalDateTime story =
            LocalDateTime.of(
                    2030, 1, 1, 23, 0
            );

    @Test
    void catchUpNarrativeBecomesContextForFollowingLiveDialogue() {
        when(clock.now())
                .thenReturn(real);

        when(memoryRecallService.recallForConversation(
                anyLong(),
                anyLong(),
                any(LocalDateTime.class),
                anyInt(),
                anyString(),
                anyBoolean()
        )).thenReturn(List.of());

        var character =
                new Character("자동 행동 QA");

        em.persist(character);

        em.persist(
                new CharacterState(
                        character,
                        20,
                        70,
                        story
                )
        );

        var profile =
                new PlayerProfile(
                        1,
                        "하린",
                        "사서",
                        null
                );

        em.persist(profile);

        var room =
                Conversation.fresh(
                        character,
                        story
                );

        room.applyRoomState(
                20,
                70,
                story
        );

        room.selectPlayerProfile(profile);

        em.persist(room);
        em.flush();

        Long characterId = character.getId();
        Long conversationId = room.getId();

        progressService.configure(
                characterId,
                conversationId,
                true
        );

        when(clock.now())
                .thenReturn(real.plusHours(1));

        // beginChat 경계는 별도 integration test에서 검증한다. 여기서는 저장된 catch-up의 context 반영을 검증한다.
        var progress =
                progressService.resume(
                        characterId,
                        conversationId
                );

        assertThat(progress.lastReport()).isNotNull();

        var context =
                contextBuilder.build(
                        characterId,
                        conversationId,
                        ContentMode.NORMAL,
                        "돌아왔어요."
                );

        assertThat(context.simulatedAt())
                .isEqualTo(story.plusHours(1));

        assertThat(context.recentMessages())
                .extracting(DialogueMessageContext::content)
                .anyMatch(content ->
                        content.contains("휴식")
                );

    }
}
