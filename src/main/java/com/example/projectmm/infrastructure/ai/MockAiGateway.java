package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.application.ai.AiGateway;
import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.simulation.StateDelta;
import com.example.projectmm.infrastructure.ai.ConditionalOnGeminiKey;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnGeminiKey(false)
public class MockAiGateway implements AiGateway {

    @Override
    public AiDecision decide(AiContext context) {

        return new AiDecision(
                "REFLECTION",
                context.characterName() + "은 잠시 자신의 감정을 돌아보았다.",
                4,
                new StateDelta(0, 5),
                null,
                RelationshipDelta.none()
        );
    }
}