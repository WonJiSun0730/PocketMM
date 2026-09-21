package com.example.projectmm.application.memory;

import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.memory.MemoryDecision;
import org.springframework.stereotype.Component;

@Component
public class MemoryPolicy {

    private static final int MEMORY_THRESHOLD = 4;

    public MemoryDecision evaluate(
            Event event
    ) {
        if (event.getImportance()
                < MEMORY_THRESHOLD) {

            return MemoryDecision.ignore();
        }

        int strength =
                Math.min(
                        100,
                        event.getImportance() * 10
                );

        return MemoryDecision.remember(
                strength
        );
    }
}