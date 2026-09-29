package com.example.projectmm.application.simulation;

import com.example.projectmm.application.ai.AiContextBuilder;
import com.example.projectmm.application.ai.AiDecisionValidator;
import com.example.projectmm.application.ai.AiGateway;
import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.relationship.RelationshipRepository;
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
    private final RelationshipRepository relationshipRepository;

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

                    AiDecision firstDecision =
                            requestAiDecision(
                                    state,
                                    candidate,
                                    "first"
                            );

                    if (firstDecision == null) {
                        AiDecision retryDecision =
                                requestAiDecision(
                                        state,
                                        candidate,
                                        "retry"
                                );

                        if (retryDecision == null) {
                            applyFallback(
                                    state,
                                    candidate,
                                    resolvedEvents
                            );
                            continue;
                        }

                        ValidationResult retryValidation =
                                aiDecisionValidator.validate(
                                        retryDecision,
                                        candidate.occurredAt()
                                );

                        if (retryValidation.valid()) {
                            retryValidation =
                                    validateRelationshipTarget(
                                            state,
                                            retryDecision
                                    );
                        }

                        if (!retryValidation.valid()) {
                            log.warn(
                                    "Retried AI decision rejected. characterId={}, reason={}",
                                    state.getCharacter().getId(),
                                    retryValidation.reason()
                            );

                            continue;
                        }

                        applyAiDecision(
                                state,
                                candidate,
                                retryDecision,
                                resolvedEvents
                        );

                        continue;
                    }

                    ValidationResult firstValidation =
                            aiDecisionValidator.validate(
                                    firstDecision,
                                    candidate.occurredAt()
                            );

                    if (firstValidation.valid()) {
                        firstValidation =
                                validateRelationshipTarget(
                                        state,
                                        firstDecision
                                );
                    }

                    if (firstValidation.valid()) {
                        applyAiDecision(
                                state,
                                candidate,
                                firstDecision,
                                resolvedEvents
                        );

                        continue;
                    }

                    log.warn(
                            "AI decision rejected. Retrying once. characterId={}, reason={}",
                            state.getCharacter().getId(),
                            firstValidation.reason()
                    );

                    AiDecision retryDecision =
                            requestAiDecision(
                                    state,
                                    candidate,
                                    "retry"
                            );

                    if (retryDecision == null) {
                        applyFallback(
                                state,
                                candidate,
                                resolvedEvents
                        );
                        continue;
                    }

                    ValidationResult retryValidation =
                            aiDecisionValidator.validate(
                                    retryDecision,
                                    candidate.occurredAt()
                            );

                    if (retryValidation.valid()) {
                        retryValidation =
                                validateRelationshipTarget(
                                        state,
                                        retryDecision
                                );
                    }

                    if (!retryValidation.valid()) {
                        log.warn(
                                "Retried AI decision rejected. characterId={}, reason={}",
                                state.getCharacter().getId(),
                                retryValidation.reason()
                        );

                        continue;
                    }

                    applyAiDecision(
                            state,
                            candidate,
                            retryDecision,
                            resolvedEvents
                    );

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

    private AiDecision requestAiDecision(
            CharacterState state,
            EventCandidate candidate,
            String attempt
    ) {
        try {
            AiContext context =
                    aiContextBuilder.build(
                            state,
                            candidate
                    );

            return aiGateway.decide(context);

        } catch (Exception e) {
            log.error(
                    "AI decision attempt failed. characterId={}, eventType={}, attempt={}",
                    state.getCharacter().getId(),
                    candidate.eventType(),
                    attempt,
                    e
            );

            return null;
        }
    }

    private ValidationResult validateRelationshipTarget(
            CharacterState state,
            AiDecision decision
    ) {
        RelationshipDelta delta =
                decision.relationshipDelta();

        boolean hasRelationshipChange =
                delta.affinityChange() != 0
                        || delta.trustChange() != 0
                        || delta.tensionChange() != 0;

        if (hasRelationshipChange) {
            Long targetCharacterId =
                    decision.relationshipTargetCharacterId();

            boolean relationshipExists =
                    relationshipRepository
                            .findByFromCharacterIdAndToCharacterId(
                                    state.getCharacter().getId(),
                                    targetCharacterId
                            )
                            .isPresent();

            if (!relationshipExists) {
                return ValidationResult.reject(
                        "relationship target is not an existing relationship"
                );
            }
        }

        var factChange =
                decision.relationshipFactChange();

        if (factChange != null
                && factChange.hasChange()) {

            boolean relationshipExists =
                    relationshipRepository
                            .findByFromCharacterIdAndToCharacterId(
                                    state.getCharacter().getId(),
                                    factChange.targetCharacterId()
                            )
                            .isPresent();

            if (!relationshipExists) {
                return ValidationResult.reject(
                        "relationship fact target is not an existing relationship"
                );
            }
        }

        return ValidationResult.accept();
    }

    private void applyAiDecision(
            CharacterState state,
            EventCandidate candidate,
            AiDecision decision,
            List<ResolvedEvent> resolvedEvents
    ) {
        state.apply(decision.stateDelta());

        resolvedEvents.add(
                new ResolvedEvent(
                        candidate.occurredAt(),
                        decision.eventType(),
                        decision.summary(),
                        decision.importance(),
                        decision.narrativeAnchor(),
                        decision.stateDelta(),
                        decision.relationshipTargetCharacterId(),
                        decision.relationshipDelta(),
                        EventSource.AI,
                        decision.memoryAccessChange(),
                        decision.memoryDetails(),
                        decision.relationshipFactChange()
                )
        );
    }

    private void applyFallback(
            CharacterState state,
            EventCandidate candidate,
            List<ResolvedEvent> resolvedEvents
    ) {
        state.apply(candidate.stateDelta());

        resolvedEvents.add(
                new ResolvedEvent(
                        candidate.occurredAt(),
                        candidate.eventType(),
                        candidate.summary(),
                        candidate.importance(),
                        0,
                        candidate.stateDelta(),
                        null,
                        RelationshipDelta.none(),
                        EventSource.RULE,
                        null
                )
        );
    }
}
