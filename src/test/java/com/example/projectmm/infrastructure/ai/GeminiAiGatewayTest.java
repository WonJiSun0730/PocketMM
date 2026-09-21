package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.ai.AiMemoryContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiAiGatewayTest {

    @Test
    void Gemini가_구조화된_AI_판단을_반환한다() {

        String apiKey =
                System.getenv("GEMINI_API_KEY");

        assertThat(apiKey)
                .as("GEMINI_API_KEY 환경변수가 필요합니다.")
                .isNotBlank();

        GeminiAiGateway gateway =
                new GeminiAiGateway(
                        apiKey,
                        "gemini-3.6-flash",
                        new ObjectMapper()
                );

        AiContext context =
                new AiContext(
                        1L,
                        "Millie",
                        80,
                        10,
                        LocalDateTime.of(
                                2026, 9, 21,
                                13, 0
                        ),
                        "MOOD_CHANGE",
                        "기분이 크게 가라앉아 있었다.",
                        List.of(
                                new AiMemoryContext(
                                        "마르바스와 중요한 대화를 나누었고, 그 대화에서 위로를 받았다.",
                                        8,
                                        75,
                                        LocalDateTime.of(
                                                2026, 9, 16,
                                                20, 0
                                        )
                                )
                        ),
                        List.of()
                );

        AiDecision decision =
                gateway.decide(context);

        System.out.println(
                "eventType = " + decision.eventType()
        );

        System.out.println(
                "summary = " + decision.summary()
        );

        System.out.println(
                "importance = " + decision.importance()
        );

        System.out.println(
                "energyChange = "
                        + decision.stateDelta().energyChange()
        );

        System.out.println(
                "moodChange = "
                        + decision.stateDelta().moodChange()
        );

        assertThat(decision.eventType())
                .isNotBlank();

        assertThat(decision.summary())
                .isNotBlank();

        assertThat(decision.importance())
                .isBetween(0, 10);

        assertThat(decision.stateDelta())
                .isNotNull();
    }
}