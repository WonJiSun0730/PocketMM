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
                    "(시간|날짜|시각|하루|이튿|모레|밤새|며칠|내일|다음\\s*(?:날|주|달|해)|"
                            + "[0-9]{1,2}\\s*시|[0-9]{1,2}:[0-9]{2}|"
                            + "(?:[0-9]+|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|몇|며칠)\\s*(?:분|시간|일|주|개월|년)|"
                            + "(?:잠시|잠깐|한참|얼마간|오래)\\s*(?:동안\\s*)?(?:기다|자|잤|쉬|머물|보내|보냈|지내|지냈)|"
                            + "기다렸|기다렸다|(?:나중|잠시|잠깐|한참|얼마)[이가은는\\s]*(?:후|뒤|지나|지났|흘)|"
                            + "(?<![가-힣])(?:아침|오전|점심|오후|저녁|밤)"
                            + "(?=$|[\\s,.!?*\"“”]|이|에|으로|부터|까지|쯤|때|을|의|은)|"
                            + "(?<![가-힣])낮(?=$|[\\s,.!?*\"“”]|이|에|으로|부터|까지|쯤|때|을|의))"
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
