package com.example.projectmm.application.chat;

import com.example.projectmm.application.simulation.CatchUpEventCandidateService;
import com.example.projectmm.application.simulation.CatchUpExecutionService;
import com.example.projectmm.application.simulation.CatchUpSegmentService;
import com.example.projectmm.application.simulation.CatchUpSimulationService;
import com.example.projectmm.application.simulation.EventCandidateValidator;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.simulation.CatchUpExecutionResult;
import com.example.projectmm.domain.simulation.EventCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BeginChatCatchUpResolver {

    private final ConversationRepository conversations;
    private final CharacterStateRepository states;
    private final CatchUpSimulationService simulation;
    private final CatchUpSegmentService segments;
    private final CatchUpEventCandidateService candidates;
    private final EventCandidateValidator validator;
    private final CatchUpExecutionService catchUpExecution;

    public record Resolution(
            CharacterState state,
            List<CatchUpExecutionResult> aiResults,
            List<EventCandidate> restCandidates,
            String pauseReason,
            boolean capped
    ) {}

    public Resolution resolve(
            BeginChatPreparationService.Preparation preparation
    ) {
        Conversation room =
                conversations.findById(
                        preparation.conversationId()
                ).orElseThrow();

        var baseline =
                states.findByCharacterId(
                        preparation.characterId()
                ).orElseThrow();

        var state =
                new CharacterState(
                        room.getCharacter(),
                        preparation.roomEnergy() == null
                                ? baseline.getEnergy()
                                : preparation.roomEnergy(),
                        preparation.roomMood() == null
                                ? baseline.getMood()
                                : preparation.roomMood(),
                        baseline.getMemoryAbility(),
                        preparation.simulatedAt()
                );

        var interval =
                simulation.calculate(
                        preparation.simulatedAt(),
                        preparation.simulatedAt()
                                .plusSeconds(
                                        preparation.absenceSeconds()
                                )
                );

        var aiResults =
                new ArrayList<CatchUpExecutionResult>();

        var restCandidates =
                new ArrayList<EventCandidate>();

        String pauseReason = null;

        for (var segment :
                segments.createSegments(
                        interval.from(),
                        interval.to()
                )) {

            var proposed =
                    candidates.generate(
                            state,
                            segment
                    );

            if (proposed.stream().anyMatch(candidate ->
                    !candidate.requiresAi()
                            && (
                            !"REST".equals(candidate.eventType())
                                    || !validator.validate(candidate).valid()
                    )
            )) {
                pauseReason =
                        "판단이 필요한 행동이 있어 자동 진행을 멈췄습니다. "
                                + "대화로 장면을 이어가 주세요.";

                break;
            }

            if (proposed.stream().anyMatch(
                    EventCandidate::requiresAi
            )) {
                var result =
                        catchUpExecution.resolve(
                                state,
                                segment,
                                proposed,
                                room
                        );

                aiResults.add(
                        result
                );

                continue;
            }

            for (var candidate : proposed) {
                state.apply(
                        candidate.stateDelta()
                );

                restCandidates.add(
                        candidate
                );
            }

            state.advanceSimulatedAt(
                    segment.end()
            );
        }

        return new Resolution(
                state,
                List.copyOf(aiResults),
                List.copyOf(restCandidates),
                pauseReason,
                interval.capped()
        );
    }
}