package com.example.projectmm.application.time;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SceneClockInterpreterTest {
    private final LocalDateTime current = LocalDateTime.of(2026, 10, 8, 15, 7);
    private final RuleBasedTimeInterpreter rule = new RuleBasedTimeInterpreter();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "아까 올라오면서 편의점 있더라. 난 참치마요 먹고 싶어. *둘은 식사를 했다. 다시 상담소에 돌아오니 3시 25분이다.*|2026-10-08T15:25",
            "시계는 오후 3시 25분을 가리켰다.|2026-10-08T15:25",
            "지금은 15:25이다.|2026-10-08T15:25",
            "오후 3시 반이다.|2026-10-08T15:30",
            "3일 후 돌아오니 오후 3시 25분이다.|2026-10-11T15:25"
    })
    void currentClockOverridesActivityEstimates(String input, LocalDateTime target) {
        var parser = mock(AiTimeParser.class);
        var hybrid = new HybridTimeInterpreter(rule, new AiTimeInterpreter(parser, new TimeChangeResolver()));
        assertThat(hybrid.interpret(input, current).timeChange().newTime()).isEqualTo(target);
        verifyNoInteractions(parser);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "상담은 3시 반이다.",
            "상담은 3시 25분이다.",
            "내일 만나기로 한 시각은 오후 3시 25분이다.",
            "어제 돌아온 시각은 오후 3시 25분이었다.",
            "오후 25시 80분이다."
    })
    void appointmentsMemoriesAndInvalidClocksDoNotChangeTime(String input) {
        assertThat(rule.interpret(input, current).matched()).isFalse();
    }

    @Test
    void equalClockStillEstablishesExactEndpoint() {
        var result = rule.interpret("지금은 3시 7분이다.", current);
        assertThat(result.matched()).isTrue();
        assertThat(result.timeChange().changed()).isFalse();
        assertThat(result.timeChange().expectedElapsedMinutes()).isZero();
    }
}
