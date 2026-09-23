package com.example.projectmm.application.chat;

import com.example.projectmm.domain.dialogue.DialogueStateChange;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ChatStateUpdateTest {
    @Test void boundedProposalsClampFinalStateAndKeepIndependentEvidence() {
        var update = ChatStateUpdate.resolve(3, 98, "*서류를 옮겨 지쳤다. 칭찬에 기뻐했다.*",
                new DialogueStateChange(-10, 5, "서류를 옮겨 지쳤다", "칭찬에 기뻐했다"));
        assertThat(update.status()).isEqualTo("APPLIED");
        assertThat(update.energy()).isZero();
        assertThat(update.mood()).isEqualTo(100);
        assertThat(update.previousEnergy()).isEqualTo(3);
        assertThat(update.previousMood()).isEqualTo(98);
    }

    @Test void malformedOrUngroundedProposalLeavesBothDimensionsUntouched() {
        for (var proposal : java.util.List.of(
                DialogueStateChange.invalid(), new DialogueStateChange(-11, 0, "쉬었다", ""),
                new DialogueStateChange(0, 11, "", "쉬었다"),
                new DialogueStateChange(5, 0, "운동했다", ""),
                new DialogueStateChange(5, 0, "", ""),
                new DialogueStateChange(5, 0, "가".repeat(241), ""))) {
            var result = ChatStateUpdate.resolve(70, 60, "*쉬었다.*", proposal);
            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.energy()).isEqualTo(70);
            assertThat(result.mood()).isEqualTo(60);
        }
        assertThat(ChatStateUpdate.resolve(70, 60, "안녕", null).status()).isEqualTo("REJECTED");
    }

    @Test void neutralTurnAndAlreadyFullStateDoNotDrift() {
        assertThat(ChatStateUpdate.resolve(70, 70, "안녕하세요", DialogueStateChange.none()).status()).isEqualTo("UNCHANGED");
        var full = ChatStateUpdate.resolve(100, 100, "*편안히 쉬었다.*", new DialogueStateChange(5, 0, "편안히 쉬었다", ""));
        assertThat(full.status()).isEqualTo("UNCHANGED");
        assertThat(full.energy()).isEqualTo(100);
    }
}
