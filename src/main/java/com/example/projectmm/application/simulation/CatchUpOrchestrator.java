package com.example.projectmm.application.simulation;

import com.example.projectmm.application.ai.AiContextBuilder;
import com.example.projectmm.application.ai.AiDecisionValidator;
import com.example.projectmm.application.ai.AiGateway;
import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.simulation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatchUpOrchestrator {

    private final CatchUpSimulationService catchUpSimulationService;
    private final CatchUpSegmentService catchUpSegmentService;
    private final CatchUpEventCandidateService eventCandidateService;
    private final EventCandidateValidator validator;
    private final AiContextBuilder aiContextBuilder;
    private final AiGateway aiGateway;
    private final AiDecisionValidator aiDecisionValidator;

    public CatchUpExecutionResult execute(
            CharacterState state,
            LocalDateTime currentTime
    ) {
        CatchUpResult catchUp =
                catchUpSimulationService.calculate(
                        state.getSimulatedAt(),
                        currentTime
                );

        List<CatchUpSegment> segments =
                catchUpSegmentService.createSegments(
                        catchUp.from(),
                        catchUp.to()
                );

        List<EventCandidate> candidates =
                new ArrayList<>();

        List<ResolvedEvent> resolvedEvents =
                new ArrayList<>();

        for (CatchUpSegment segment : segments) {

            List<EventCandidate> segmentCandidates =
                    eventCandidateService.generate(
                            state,
                            segment
                    );

            for (EventCandidate candidate : segmentCandidates) {

                if (candidate.requiresAi()) {

                    try {
                        AiContext context =
                                aiContextBuilder.build(
                                        state,
                                        candidate
                                );

                        AiDecision decision =
                                aiGateway.decide(context);

                        ValidationResult validation =
                                aiDecisionValidator.validate(decision, candidate.occurredAt());

                        if (!validation.valid()) {
                            log.warn(
                                    "AI decision rejected. characterId={}, reason={}",
                                    state.getCharacter().getId(),
                                    validation.reason()
                            );

                            continue;
                        }

                        state.apply(decision.stateDelta());

                        resolvedEvents.add(
                                new ResolvedEvent(
                                        candidate.occurredAt(),
                                        decision.eventType(),
                                        decision.summary(),
                                        decision.importance(),
                                        decision.stateDelta(),
                                        decision.relationshipTargetCharacterId(),
                                        decision.relationshipDelta(),
                                        EventSource.AI,
                                        decision.memoryAccessChange()
                                )
                        );

                    } catch (Exception e) {
                        log.error(
                                "AI decision failed. characterId={}, eventType={}",
                                state.getCharacter().getId(),
                                candidate.eventType(),
                                e
                        );
                    }

                    continue;
                }

                ValidationResult validation =
                        validator.validate(candidate);

                if (validation.valid()) {

                    state.apply(candidate.stateDelta());

                    resolvedEvents.add(
                            new ResolvedEvent(
                                    candidate.occurredAt(),
                                    candidate.eventType(),
                                    candidate.summary(),
                                    candidate.importance(),
                                    candidate.stateDelta(),
                                    null,
                                    RelationshipDelta.none(),
                                    EventSource.RULE
                            )
                    );
                }
            }

            state.advanceSimulatedAt(segment.end());

            candidates.addAll(segmentCandidates);
        }

        return new CatchUpExecutionResult(
                catchUp,
                segments,
                candidates,
                resolvedEvents
        );
    }
}
