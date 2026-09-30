package com.example.projectmm.application.chat;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.simulation.ResolvedEvent;

import java.util.List;

public interface CatchUpNarrativeGateway {

    String narrate(
            Character character,
            List<ResolvedEvent> events
    );
}