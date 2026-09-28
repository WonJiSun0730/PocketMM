package com.example.projectmm.domain.dialogue;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.chat.PlayerStateUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class DialogueAgencyPolicyTest {
    private final DialogueContext context = new DialogueContext(1L, "마르바스", null, 70, 70,
            LocalDateTime.of(2030, 1, 1, 12, 0), ContentMode.NORMAL, List.of(), List.of(), List.of(),
            new DialoguePlayerProfile("하린", "사서"), 70, 70, List.of());

    static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of("명시 행동", "*문을 열었다.*", "*당신은 문을 열었다. 마르바스가 고개를 들었다.*", false),
                Arguments.of("대사", "안녕하세요.", "\"안녕하세요.\" *마르바스가 인사에 답했다.*", false),
                Arguments.of("사소한 연속", "*문고리를 잡았다.*", "*당신의 손은 문고리에 얹혀 있었다.*", false),
                Arguments.of("NPC 행동", "안녕", "*마르바스는 문을 열었다. 그는 행복했다.*", false),
                Arguments.of("NPC 추측", "*무표정이었다.*", "*당신은 두려움을 느꼈던 것 같다고 마르바스는 짐작했다.*", false),
                Arguments.of("NPC 대사", "안녕", "\"당신은 도망쳤다면서요?\"", false),
                Arguments.of("따옴표 없는 NPC 대사", "안녕", "당신은 도망쳤다면서요. *마르바스가 물었다.*", false),
                Arguments.of("명시 감정", "*두려움을 느꼈다.*", "*당신은 두려움을 느꼈다.*", false),
                Arguments.of("새 행동", "*문고리를 잡았다.*", "*당신은 문을 열었다.*", true),
                Arguments.of("중요 선택", "어떻게 할까?", "*당신은 제안을 승낙했다.*", true),
                Arguments.of("감정 확정", "*무표정이었다.*", "*당신은 두려움을 느꼈다.*", true),
                Arguments.of("생각 결정", "*가만히 있었다.*", "*당신은 떠나기로 결심했다.*", true),
                Arguments.of("내면 생각", "*가만히 있었다.*", "*당신은 집에 가야 한다고 생각했다.*", true),
                Arguments.of("명시 생각 재서술", "*집에 가야 한다고 생각했다.*", "*당신은 집에 가야 한다고 생각했다.*", false),
                Arguments.of("성공 실패 구별", "*공격에 실패했다.*", "*당신은 공격에 성공했다.*", true),
                Arguments.of("감정 구별", "*슬펐다.*", "*당신은 행복했다.*", true),
                Arguments.of("계획", "도망갈까 고민했다.", "*당신은 도망쳤다.*", true),
                Arguments.of("희망", "문을 열고 싶다.", "*당신은 문을 열었다.*", true),
                Arguments.of("가정", "만약 문을 열었다면?", "*당신은 문을 열었다.*", true),
                Arguments.of("부정", "문을 열고 들어가지 않았다.", "*당신은 문을 열고 방에 들어갔다.*", true),
                Arguments.of("성공", "*칼을 휘둘렀다.*", "*당신은 공격에 성공했다.*", true),
                Arguments.of("실패", "*칼을 휘둘렀다.*", "*당신은 공격에 실패했다.*", true),
                Arguments.of("다음 행동", "*문을 열었다.*", "*당신은 문을 열고 방에 들어갔다.*", true),
                Arguments.of("프로필 이름", "*문고리를 잡았다.*", "*하린은 문을 열었다.*", true),
                Arguments.of("다른 주체 근거", "*마르바스는 도망쳤다.*", "*당신은 도망쳤다.*", true),
                Arguments.of("단순 질문", "문을 열었다고요?", "*당신은 문을 열었다.*", true),
                Arguments.of("서술 내 NPC 주체", "안녕", "*당신은 서 있고 마르바스는 도망쳤다.*", false));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("scenarios")
    void scenarioEval(String name, String input, String reply, boolean rejected) {
        assertThat(DialogueAgencyPolicy.violates(reply, input, context)).isEqualTo(rejected);
        var turn = new DialogueTurn(reply, DialogueStateChange.none());
        if (!rejected) assertThat(DialogueAgencyPolicy.enforce(turn, input, context)).isSameAs(turn);
        else assertThat(DialogueAgencyPolicy.enforce(turn, input, context).reply()).doesNotContain(reply);
    }

    @Test void stateEvidenceCannotBeTakenFromHypotheticalNegatedOrPlannedActivity() {
        var change = new DialoguePlayerStateChange(-5, 0, "운동했다", "");
        for (String input : List.of("운동했다면 지쳤을 거야.", "운동했다고 상상했다.",
                "운동했다는 말은 거짓이야.", "운동했다?", "운동했다고 생각했지만 하지 않았다.")) {
            var result = PlayerStateUpdate.resolve(70, 70, input, change);
            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.energy()).isEqualTo(70);
        }
        assertThat(PlayerStateUpdate.resolve(70, 70, "*운동했다.*", change).energy()).isEqualTo(65);
        assertThat(PlayerStateUpdate.resolve(70, 70, "*두 시간 동안 운동했다.*", change).energy()).isEqualTo(65);
        assertThat(PlayerStateUpdate.resolve(70, 70, "*운동했다.*",
                new DialoguePlayerStateChange(-5, 0, "*운동했다.*", "")).energy()).isEqualTo(65);
    }
}
