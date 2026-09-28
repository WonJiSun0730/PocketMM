package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.chat.PlayerStateUpdate;
import com.example.projectmm.application.chat.ChatStateUpdate;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.dialogue.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class GeminiAgencyResponseTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiDialogueGateway gateway = new GeminiDialogueGateway("test", "test", mapper);
    private final DialogueContext context = new DialogueContext(1L, "NPC", null, 70, 70,
            LocalDateTime.of(2030, 1, 1, 12, 0), ContentMode.NORMAL, List.of(), List.of(), List.of(),
            new DialoguePlayerProfile("하린", "사서"), 70, 70, List.of());

    @Test void rejectedReplyCannotApplyNpcPlayerOrCustomStateAndNeverLeaksJson() {
        String body = """
                {"reply":"*당신은 문을 열고 방에 들어갔다.*", "state":{
                "energyDelta":-4,"moodDelta":0,"energyEvidence":"문을 열고","moodEvidence":""},
                "playerState":{"energyDelta":-3,"moodDelta":0,"energyEvidence":"문고리를 잡았다","moodEvidence":""},
                "customStateChanges":[{"definitionId":1,"delta":-5}]}
                """;
        String input = "*문고리를 잡았다.*";
        var turn = gateway.parseResponse(envelope(body), context, input);
        assertThat(turn.reply()).doesNotContain("들어갔다", "definitionId", "playerState", "reply", "delta");
        assertThat(turn.customStateChanges()).isEmpty();
        assertThat(PlayerStateUpdate.resolve(70, 70, input, turn.playerStateChange()).energy()).isEqualTo(70);
        assertThat(ChatStateUpdate.resolve(70, 70, turn.reply(), turn.stateChange()).energy()).isEqualTo(70);
    }

    @Test void plainRpFallbackUsesSameGuardButAllowedStructuredTurnKeepsMetadata() {
        assertThat(gateway.parseResponse(envelope("*당신은 도망쳤다.*"), context, "도망갈까 고민했다.").reply())
                .doesNotContain("도망쳤다");
        var allowed = gateway.parseResponse(envelope("""
                {"reply":"*당신은 문을 열었다.*","customStateChanges":[{"definitionId":1,"delta":-5}]}
                """), context, "*문을 열었다.*");
        assertThat(allowed.reply()).isEqualTo("*당신은 문을 열었다.*");
        assertThat(allowed.customStateChanges()).hasSize(1);
    }

    private String envelope(String text) {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP",
                "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }
}
