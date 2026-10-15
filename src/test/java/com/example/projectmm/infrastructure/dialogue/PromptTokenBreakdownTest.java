package com.example.projectmm.infrastructure.dialogue;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PromptTokenBreakdownTest {
    @Test void separatesRetrievedMemoryFromInstructionsAndCurrentInputFromHistory() {
        var parts = PromptTokenBreakdown.estimate("Rules\nRelevant recalled memories of currently active characters:\nA remembered fact\nReturn the response using the supplied strict JSON schema.",
                List.of(Map.of("content","previous user"),Map.of("content","previous assistant"),Map.of("content","현재 질문")),"{}");
        assertThat(parts.systemTokens()).isPositive();
        assertThat(parts.memoryTokens()).isPositive();
        assertThat(parts.historyTokens()).isPositive();
        assertThat(parts.currentInputTokens()).isPositive();
        var noHistory = PromptTokenBreakdown.estimate("Rules",List.of(Map.of("content","현재 질문")),"{}");
        assertThat(noHistory.historyTokens()).isZero();
        assertThat(noHistory.memoryTokens()).isZero();
        assertThat(noHistory.currentInputTokens()).isEqualTo(parts.currentInputTokens());
    }
}
