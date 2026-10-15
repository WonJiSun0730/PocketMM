package com.example.projectmm.application.memory;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PocketMemoryAudienceSnapshotTest {
    @Test
    void audienceSnapshotDoesNotChangeWhenCastDepartsAfterUserSpeaks() {
        var heardAtInput = List.of(10L, 20L);
        var event = new PocketUserSpeechCommitted(37L, 123L,
                "편지는 진료소 문틈에 있었어요.", LocalDateTime.of(2026, 10, 15, 12, 0),
                List.copyOf(heardAtInput));
        var activeAfterReply = List.of(10L);
        assertThat(event.audienceCharacterIds()).containsExactly(10L, 20L);
        assertThat(activeAfterReply).doesNotContain(20L);
    }

    @Test
    void narrationIsNotTreatedAsPlayerTestimony() {
        assertThat(SpokenText.from("*엘레노어는 문을 바라봤다.*\n-편지는 문틈에 있었어요."))
                .isEqualTo("-편지는 문틈에 있었어요.");
        assertThat(SpokenText.from("*엘레노어는 문을 바라봤다.*")).isEmpty();
    }
}
