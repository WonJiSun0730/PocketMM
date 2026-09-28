package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.domain.dialogue.DialogueStateChange;
import com.example.projectmm.domain.dialogue.DialogueTurn;
import com.example.projectmm.domain.state.CustomStateChangeProposal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class GeminiCustomStateResponseTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiDialogueGateway gateway = new GeminiDialogueGateway("test", "test", mapper);

    @Test void transportsSingleAndMultipleDeltasWithoutCalculatingFinalValues() {
        assertThat(parse("[{\"definitionId\":1,\"delta\":-5}]").customStateChanges())
                .containsExactly(new CustomStateChangeProposal(1L, -5.0));
        assertThat(parse("[{\"definitionId\":1,\"delta\":-5.25},{\"definitionId\":9223372036854775807,\"delta\":0}]")
                .customStateChanges()).containsExactly(new CustomStateChangeProposal(1L, -5.25),
                        new CustomStateChangeProposal(Long.MAX_VALUE, 0.0));
    }

    @Test void absentOrEmptyListMeansNoProposal() {
        assertThat(parse("[]").customStateChanges()).isEmpty();
        assertThat(gateway.parseResponse(envelope("{\"reply\":\"안녕하세요\"}")).customStateChanges()).isEmpty();
        assertThat(gateway.parseResponse(envelope("*고개를 끄덕였다.*")).customStateChanges()).isEmpty();
    }

    @Test void invalidEntryDiscardsWholeBatchButPreservesReplyAndNpcState() {
        // 앞의 정상 항목도 남기지 않아 부분 적용과 중복 합산을 방지한다.
        for (String invalid : List.of("null", "1", "[]", "{}",
                "{\"delta\":1}", "{\"definitionId\":1}",
                "{\"definitionId\":null,\"delta\":1}", "{\"definitionId\":\"2\",\"delta\":1}",
                "{\"definitionId\":true,\"delta\":1}", "{\"definitionId\":2.5,\"delta\":1}",
                "{\"definitionId\":0,\"delta\":1}", "{\"definitionId\":-1,\"delta\":1}",
                "{\"definitionId\":9223372036854775808,\"delta\":1}",
                "{\"definitionId\":2,\"delta\":null}", "{\"definitionId\":2,\"delta\":\"5\"}",
                "{\"definitionId\":2,\"delta\":false}", "{\"definitionId\":2,\"delta\":{}}",
                "{\"definitionId\":2,\"delta\":NaN}", "{\"definitionId\":2,\"delta\":Infinity}",
                "{\"definitionId\":2,\"delta\":-Infinity}", "{\"definitionId\":2,\"delta\":1e309}",
                "{\"definitionId\":2,\"delta\":\"NaN\"}",
                "{\"definitionId\":1,\"delta\":2}",
                "{\"definitionId\":2,\"delta\":1,\"finalValue\":80}")) {
            assertPreserved(parse("[{\"definitionId\":1,\"delta\":-5}," + invalid + "]"));
        }
    }

    @Test void malformedListShapePreservesOtherFieldsButBrokenJsonIsNotLeaked() {
        for (String value : List.of("null", "{}", "5", "\"bad\"", "true")) assertPreserved(parse(value));
        assertThatThrownBy(() -> parse("[{")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> gateway.parseResponse(envelope("*본문*\n\"customStateChanges\":[]")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void dialogueTurnKeepsCompatibilityAndDefensiveImmutableCopy() {
        assertThat(new DialogueTurn("본문", DialogueStateChange.none()).customStateChanges()).isEmpty();
        assertThat(new DialogueTurn("본문", DialogueStateChange.none(), null).customStateChanges()).isEmpty();
        var source = new ArrayList<>(List.of(new CustomStateChangeProposal(1L, -5.0)));
        var turn = new DialogueTurn("본문", DialogueStateChange.none(), source);
        source.clear();
        assertThat(turn.customStateChanges()).hasSize(1);
        assertThatThrownBy(() -> turn.customStateChanges().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertPreserved(DialogueTurn turn) {
        assertThat(turn.reply()).isEqualTo("*쉬었다.*");
        assertThat(turn.stateChange()).isEqualTo(new DialogueStateChange(2, 0, "쉬었다", ""));
        assertThat(turn.customStateChanges()).isEmpty();
    }

    private DialogueTurn parse(String changes) {
        return gateway.parseResponse(envelope("{\"reply\":\"*쉬었다.*\",\"state\":{\"energyDelta\":2,"
                + "\"moodDelta\":0,\"energyEvidence\":\"쉬었다\",\"moodEvidence\":\"\"},\"customStateChanges\":" + changes + "}"));
    }

    private String envelope(String body) {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP",
                "content", Map.of("parts", List.of(Map.of("text", body)))))));
    }
}
