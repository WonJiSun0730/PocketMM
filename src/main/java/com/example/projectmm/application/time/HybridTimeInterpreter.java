package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.TimeInterpretation;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Primary
public class HybridTimeInterpreter implements TimeInterpreter {

    private final RuleBasedTimeInterpreter ruleBasedTimeInterpreter;

    public HybridTimeInterpreter(
            RuleBasedTimeInterpreter ruleBasedTimeInterpreter
    ) {
        this.ruleBasedTimeInterpreter = ruleBasedTimeInterpreter;
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
            return ruleResult;
        }

        return TimeInterpretation.noMatch();
    }
}