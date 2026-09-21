package com.example.projectmm.application.ai;

import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.ai.AiDecision;

public interface AiGateway {

    AiDecision decide(AiContext context);
}