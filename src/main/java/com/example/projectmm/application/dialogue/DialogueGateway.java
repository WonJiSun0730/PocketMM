package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.dialogue.DialogueContext;

public interface DialogueGateway {

    String generate(
            DialogueContext context,
            String userMessage
    );
}