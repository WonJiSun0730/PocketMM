package com.example.projectmm.application.chat;

import com.example.projectmm.application.simulation.CatchUpEventCandidateService;
import com.example.projectmm.application.simulation.CatchUpExecutionService;
import com.example.projectmm.application.simulation.CatchUpSegmentService;
import com.example.projectmm.application.simulation.CatchUpSimulationService;
import com.example.projectmm.application.simulation.EventCandidateValidator;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.simulation.CatchUpExecutionResult;
import com.example.projectmm.domain.simulation.CatchUpResult;
import com.example.projectmm.domain.simulation.CatchUpSegment;
import com.example.projectmm.domain.simulation.EventCandidate;
import com.example.projectmm.domain.simulation.ResolvedEvent;
import com.example.projectmm.domain.simulation.SegmentPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeginChatCatchUpResolverTest {

    @Mock
    private ConversationRepository conversations;

    @Mock
    private CharacterStateRepository states;

    @Mock
    private CatchUpSimulationService simulation;

    @Mock
    private CatchUpSegmentService segments;

    @Mock
    private CatchUpEventCandidateService candidates;

    @Mock
    private EventCandidateValidator validator;

    @Mock
    private CatchUpExecutionService catchUpExecution;

    @Test
    void 이전_AI_사건_요약을_다음_segment의_transient_narrative로_전달한다() {

        LocalDateTime from =
                LocalDateTime.of(
                        2026, 9, 21,
                        12, 0
                );

        LocalDateTime middle =
                LocalDateTime.of(
                        2026, 9, 21,
                        17, 0
                );

        LocalDateTime to =
                LocalDateTime.of(
                        2026, 9, 21,
                        19, 0
                );

        Character character =
                new Character("Millie");

        Conversation room =
                new Conversation(
                        character,
                        from
                );

        CharacterState baseline =
                new CharacterState(
                        character,
                        80,
                        70,
                        from
                );

        CatchUpSegment firstSegment =
                new CatchUpSegment(
                        from,
                        middle,
                        SegmentPeriod.DAYTIME
                );

        CatchUpSegment secondSegment =
                new CatchUpSegment(
                        middle,
                        to,
                        SegmentPeriod.EVENING
                );

        EventCandidate firstCandidate =
                mock(EventCandidate.class);

        EventCandidate secondCandidate =
                mock(EventCandidate.class);

        when(firstCandidate.requiresAi())
                .thenReturn(true);

        when(secondCandidate.requiresAi())
                .thenReturn(true);

        ResolvedEvent firstResolvedEvent =
                mock(ResolvedEvent.class);

        when(firstResolvedEvent.summary())
                .thenReturn(
                        "밀리는 친구에게 연락해 안부를 물었다."
                );

        CatchUpExecutionResult firstResult =
                mock(CatchUpExecutionResult.class);

        CatchUpExecutionResult secondResult =
                mock(CatchUpExecutionResult.class);

        when(firstResult.resolvedEvents())
                .thenReturn(
                        List.of(firstResolvedEvent)
                );

        when(secondResult.resolvedEvents())
                .thenReturn(
                        List.of()
                );

        when(conversations.findById(any()))
                .thenReturn(
                        java.util.Optional.of(room)
                );

        when(states.findByCharacterId(any()))
                .thenReturn(
                        java.util.Optional.of(baseline)
                );

        CatchUpResult interval =
                new CatchUpResult(
                        from,
                        to,
                        Duration.between(from, to),
                        Duration.between(from, to),
                        false
                );

        when(simulation.calculate(from, to))
                .thenReturn(interval);

        when(segments.createSegments(from, to))
                .thenReturn(
                        List.of(
                                firstSegment,
                                secondSegment
                        )
                );

        when(candidates.generate(
                any(CharacterState.class),
                any(CatchUpSegment.class)
        )).thenReturn(
                List.of(firstCandidate),
                List.of(secondCandidate)
        );

        when(catchUpExecution.resolve(
                any(CharacterState.class),
                any(CatchUpSegment.class),
                anyList(),
                any(Conversation.class),
                anyList()
        )).thenReturn(
                firstResult,
                secondResult
        );

        BeginChatCatchUpResolver resolver =
                new BeginChatCatchUpResolver(
                        conversations,
                        states,
                        simulation,
                        segments,
                        candidates,
                        validator,
                        catchUpExecution
                );

        BeginChatPreparationService.Preparation preparation =
                new BeginChatPreparationService.Preparation(
                        1L,
                        10L,
                        "claim-token",
                        from,
                        to,
                        Duration.between(
                                from,
                                to
                        ).getSeconds(),
                        true,
                        null,
                        from,
                        80,
                        70
                );

        resolver.resolve(preparation);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> narrativeCaptor =
                ArgumentCaptor.forClass(List.class);

        org.mockito.Mockito.verify(
                catchUpExecution,
                org.mockito.Mockito.times(2)
        ).resolve(
                any(CharacterState.class),
                any(CatchUpSegment.class),
                anyList(),
                any(Conversation.class),
                narrativeCaptor.capture()
        );

        List<List<String>> captured =
                narrativeCaptor.getAllValues();

        assertThat(captured)
                .hasSize(2);

        assertThat(captured.get(1))
                .containsExactly(
                        "밀리는 친구에게 연락해 안부를 물었다."
                );
    }
}