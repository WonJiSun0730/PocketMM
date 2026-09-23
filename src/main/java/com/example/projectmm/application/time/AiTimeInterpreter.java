package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.AiTimeParseResult;
import com.example.projectmm.domain.time.TimeChange;
import com.example.projectmm.domain.time.TimeInterpretation;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AiTimeInterpreter {

    private final AiTimeParser aiTimeParser;
    private final TimeChangeResolver timeChangeResolver;

    public AiTimeInterpreter(
            AiTimeParser aiTimeParser,
            TimeChangeResolver timeChangeResolver
    ) {
        this.aiTimeParser = aiTimeParser;
        this.timeChangeResolver = timeChangeResolver;
    }

    public TimeInterpretation interpret(
            String userInput,
            LocalDateTime currentTime
    ) {
        AiTimeParseResult parseResult =
                aiTimeParser.parse(
                        userInput,
                        currentTime
                );

        if (parseResult == null || !parseResult.matched()) {
            return TimeInterpretation.noMatch();
        }

        TimeChange timeChange =
                timeChangeResolver.resolve(
                        parseResult,
                        currentTime
                );

        return TimeInterpretation.matched(timeChange);
    }
}