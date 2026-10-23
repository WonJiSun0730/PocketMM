package com.example.projectmm.application.time;

import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import com.example.projectmm.domain.time.TimeInterpretation;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HybridTimeInterpreterTest {
    final LocalDateTime now=LocalDateTime.of(1898,11,18,22,0);
    final AiTimeInterpreter ai=mock(AiTimeInterpreter.class);
    @ParameterizedTest @ValueSource(strings={
        "그녀는 낮게 말했다.","엘레노어는 몸을 낮췄다.","낮은 목소리로 속삭였다.",
        "차체 뒤쪽을 확인하세요.","상처를 확인한 뒤 붕대를 감았다.","다음 교차로에서 우회하세요.",
        "후문을 봉쇄하세요.","날카로운 소리가 들렸다.","잠시 바라보다 고개를 끄덕였다.",
        "곧 알려 주세요. 이름은 나중에 말해도 됩니다."})
    void ordinaryActionsNeverCallTimeAi(String input) {
        var interpreter=new HybridTimeInterpreter(new RuleBasedTimeInterpreter(),ai);
        assertThat(interpreter.interpret(input,now).matched()).isFalse();
        verifyNoInteractions(ai);
    }
    @ParameterizedTest @ValueSource(strings={"내일 오전 9시로 시간을 바꿔 줘.","다음 날 아침으로 넘긴다.",
        "한참 기다렸다.","열다섯 분 동안 기다렸다.","밤새 쉬었다.","낮까지 기다리겠다.","아침이 되었다.","지금 몇 시각인지 확인해 줘.",
        "며칠이 흘렀다.","잠시 후 문이 열렸다.","한참 지나 돌아왔다."})
    void genuineTemporalExpressionsStillReachFallbackWhenRulesCannotResolve(String input) {
        var rule=mock(RuleBasedTimeInterpreter.class);when(rule.interpret(input,now)).thenReturn(TimeInterpretation.noMatch());
        when(ai.interpret(input,now)).thenReturn(TimeInterpretation.noMatch());
        new HybridTimeInterpreter(rule,ai).interpret(input,now);
        verify(ai).interpret(input,now);
    }
    @Test void completedDurationKeepsExactRuleResultWithoutAi() {
        var result=new HybridTimeInterpreter(new RuleBasedTimeInterpreter(),ai).interpret("두 시간 후",now);
        assertThat(result.matched()).isTrue();assertThat(result.timeChange().newTime()).isEqualTo(now.plusHours(2));
        verifyNoInteractions(ai);
    }
}
