package com.example.projectmm.application.chat;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.simulation.ResolvedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!gemini")
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