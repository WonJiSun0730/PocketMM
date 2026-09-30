package com.example.projectmm.application.chat;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.simulation.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.player.PlayerProfile;
import com.example.projectmm.domain.simulation.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({
        ConversationProgressService.class,
        CatchUpSimulationService.class,
        CatchUpSegmentService.class,
        CatchUpEventCandidateService.class,
        EventCandidateValidator.class,
        CatchUpClaimService.class,
        BeginChatPreparationService.class,
        BeginChatCatchUpResolver.class,
        BeginChatFinalizationService.class,
        com.example.projectmm.application.state.CustomStateProposalService.class,
        com.example.projectmm.application.relationship.RelationshipChangeService.class,
        com.example.projectmm.application.relationship.RelationshipFactService.class
})
abstract class ConversationProgressTestSupport {
    @Autowired EntityManager em;
    @Autowired CatchUpClaimService catchUpClaims;
    @Autowired ConversationProgressService service;
    @Autowired ConversationRepository rooms;
    @Autowired ConversationCatchUpRepository reports;
    @Autowired ChatMessageRepository messages;
    @Autowired MemoryRepository memories;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired
    BeginChatPreparationService beginChatPreparation;
    @Autowired
    BeginChatFinalizationService beginChatFinalization;
    @MockitoBean PresenceClock clock;
    @MockitoBean CatchUpExecutionService execution;
    @MockitoBean CatchUpNarrativeComposer narrativeComposer;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean CatchUpEventCandidateService candidateService;
    final LocalDateTime real = LocalDateTime.of(2026, 9, 23, 12, 0);
    final LocalDateTime story = LocalDateTime.of(2030, 1, 1, 23, 0);

    @BeforeEach
    void time() {
        when(clock.now()).thenReturn(real);

        when(execution.resolve(
                org.mockito.ArgumentMatchers.any(CharacterState.class),
                org.mockito.ArgumentMatchers.any(CatchUpSegment.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(Conversation.class),
                org.mockito.ArgumentMatchers.anyList()
        )).thenAnswer(invocation -> {
            CharacterState state =
                    invocation.getArgument(0);

            CatchUpSegment segment =
                    invocation.getArgument(1);

            state.advanceSimulatedAt(
                    segment.end()
            );

            var duration =
                    java.time.Duration.between(
                            segment.start(),
                            segment.end()
                    );

            return new CatchUpExecutionResult(
                    new CatchUpResult(
                            segment.start(),
                            segment.end(),
                            duration,
                            duration,
                            false
                    ),
                    java.util.List.of(segment),
                    java.util.List.of(),
                    java.util.List.of()
            );
        });

        when(execution.execute(
                org.mockito.ArgumentMatchers.any(CharacterState.class),
                org.mockito.ArgumentMatchers.any(CatchUpSegment.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(Conversation.class)
        )).thenAnswer(invocation -> {
            CharacterState state =
                    invocation.getArgument(0);

            CatchUpSegment segment =
                    invocation.getArgument(1);

            state.advanceSimulatedAt(
                    segment.end()
            );

            return java.util.List.of();
        });
    }

    Conversation fixture(boolean independent, int energy, int mood) {
        var character = new Character("자동 행동 QA");
        em.persist(character);
        em.persist(new CharacterState(character, energy, mood, story));
        var profile = new PlayerProfile(
                Math.toIntExact(character.getId()),
                "하린",
                "사서",
                null
        );
        em.persist(profile);
        var room = independent ? Conversation.fresh(character, story) : new Conversation(character, story);
        if (independent) room.applyRoomState(energy, mood, story);
        room.selectPlayerProfile(profile);
        em.persist(room); em.flush();
        return room;
    }
}
