package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.TimeInterpretation;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Component
@Primary
public class HybridTimeInterpreter implements TimeInterpreter {

    private static final Pattern TIME_HINT_PATTERN =
            Pattern.compile(
                    "(시간|날|하루|아침|오전|점심|낮|오후|저녁|밤|"
                            + "뒤|후|지나|흐르|다음|이튿|잠시|잠깐|"
                            + "곧|나중|한참|며칠|몇\\s*시간|[0-9]{1,2}\\s*시|[0-9]{1,2}:[0-9]{2}|기다렸|기다렸다)"
            );

    private final RuleBasedTimeInterpreter ruleBasedTimeInterpreter;
    private final AiTimeInterpreter aiTimeInterpreter;

    public HybridTimeInterpreter(
            RuleBasedTimeInterpreter ruleBasedTimeInterpreter,
            AiTimeInterpreter aiTimeInterpreter
    ) {
        this.ruleBasedTimeInterpreter = ruleBasedTimeInterpreter;
        this.aiTimeInterpreter = aiTimeInterpreter;
    }

    @Override
    public TimeInterpretation interpret(
            String userInput,
            LocalDateTime currentTime
    ) {
        TimeInterpretation ruleResult =
                ruleBasedTimeInterpreter.interpret(
                        userInput,
                        currentTime
                );

        if (ruleResult.matched()) {
            System.out.println(
                    "[TimeInterpreter] handled by RULE"
            );

            return ruleResult;
        }

        if (!shouldUseAiFallback(userInput)) {
            System.out.println(
                    "[TimeInterpreter] skipped AI - no time hint"
            );

            return TimeInterpretation.noMatch();
        }

        System.out.println(
                "[TimeInterpreter] fallback to AI"
        );

        return aiTimeInterpreter.interpret(
                userInput,
                currentTime
        );
    }

    private boolean shouldUseAiFallback(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }

        return TIME_HINT_PATTERN
                .matcher(userInput)
                .find();
    }
}
