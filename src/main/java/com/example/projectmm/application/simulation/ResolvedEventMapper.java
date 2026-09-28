package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.simulation.ResolvedEvent;
import org.springframework.stereotype.Component;

@Component
public class ResolvedEventMapper {

    public Event toEntity(
            Scene scene,
            ResolvedEvent resolvedEvent
    ) {
        return new Event(
                scene,
                resolvedEvent.occurredAt(),
                resolvedEvent.eventType(),
                resolvedEvent.summary(),
                resolvedEvent.importance(),
                resolvedEvent.narrativeAnchor()
        );
    }
}