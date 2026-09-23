package com.example.projectmm.infrastructure.time;

import com.example.projectmm.application.time.AiTimeParser;
import com.example.projectmm.domain.time.AiTimeParseResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("!gemini")
public class NoOpAiTimeParser implements AiTimeParser {

    @Override
    public AiTimeParseResult parse(
            String userInput,
            LocalDateTime currentTime
    ) {
        return new AiTimeParseResult(
                false,
                AiTimeParseResult.TimeType.UNKNOWN,
                null,
                null,
                null,
                0.0,
                null
        );
    }
}