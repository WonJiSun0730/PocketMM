package com.example.projectmm.application.chat;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.simulation.ResolvedEvent;
import com.example.projectmm.infrastructure.ai.ConditionalOnGeminiKey;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnGeminiKey(false)
public class FallbackCatchUpNarrativeGateway
        implements CatchUpNarrativeGateway {

    @Override
    public String narrate(
            Character character,
            List<ResolvedEvent> events
    ) {
        return "";
    }
}