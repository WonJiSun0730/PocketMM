package com.example.projectmm.application.chat;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.simulation.ResolvedEvent;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class CatchUpNarrativeComposer {

    private final CatchUpNarrativeGateway narrativeGateway;

    public CatchUpNarrativeComposer(
            CatchUpNarrativeGateway narrativeGateway
    ) {
        this.narrativeGateway =
                narrativeGateway;
    }

    public String compose(
            Character character,
            List<ResolvedEvent> events
    ) {
        if (character == null) {
            throw new IllegalArgumentException(
                    "character is required"
            );
        }

        if (events == null || events.isEmpty()) {
            return "";
        }

        List<ResolvedEvent> orderedEvents =
                events.stream()
                        .filter(event ->
                                event != null
                                        && event.occurredAt() != null
                        )
                        .sorted(
                                Comparator.comparing(
                                        ResolvedEvent::occurredAt
                                )
                        )
                        .toList();

        if (orderedEvents.isEmpty()) {
            return "";
        }

        try {
            String narrative =
                    narrativeGateway.narrate(
                            character,
                            orderedEvents
                    );

            if (narrative != null
                    && !narrative.isBlank()) {
                return narrative.trim();
            }

        } catch (RuntimeException ignored) {
            // Narrative generation failure must not fail reconnect.
        }

        return fallbackNarrative(
                orderedEvents
        );
    }

    private String fallbackNarrative(
            List<ResolvedEvent> events
    ) {
        return events.stream()
                .map(ResolvedEvent::summary)
                .filter(summary ->
                        summary != null
                                && !summary.isBlank()
                )
                .reduce(
                        (left, right) ->
                                left + " " + right
                )
                .orElse("");
    }
}