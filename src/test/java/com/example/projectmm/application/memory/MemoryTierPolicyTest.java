package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryTierPolicyTest {

    private final MemoryTierPolicy policy =
            new MemoryTierPolicy();

    @Test
    void newMemoryIsHot() {
        LocalDateTime now =
                LocalDateTime.of(2026, 9, 29, 12, 0);

        Memory memory =
                new Memory(
                        null,
                        null,
                        "recent memory",
                        5,
                        80,
                        now
                );

        assertThat(
                policy.determine(memory, now)
        ).isEqualTo(MemoryTier.HOT);
    }

    @Test
    void oldDetailedMemoryBecomesWarm() {
        LocalDateTime rememberedAt =
                LocalDateTime.of(2026, 8, 1, 12, 0);

        LocalDateTime now =
                LocalDateTime.of(2026, 9, 29, 12, 0);

        Memory memory =
                new Memory(
                        null,
                        null,
                        "old detailed memory",
                        5,
                        80,
                        rememberedAt
                );

        assertThat(
                policy.determine(memory, now)
        ).isEqualTo(MemoryTier.WARM);
    }

    @Test
    void recentlyReactivatedMemoryReturnsToHot() {
        LocalDateTime rememberedAt =
                LocalDateTime.of(2026, 7, 1, 12, 0);

        LocalDateTime now =
                LocalDateTime.of(2026, 9, 29, 12, 0);

        Memory memory =
                new Memory(
                        null,
                        null,
                        "reactivated memory",
                        5,
                        80,
                        rememberedAt
                );

        memory.reactivate(
                now.minusDays(3)
        );

        assertThat(
                policy.determine(memory, now)
        ).isEqualTo(MemoryTier.HOT);
    }

    @Test
    void compressedPatternMemoryBecomesCold() {
        LocalDateTime rememberedAt =
                LocalDateTime.of(2026, 7, 1, 12, 0);

        LocalDateTime now =
                LocalDateTime.of(2026, 9, 29, 12, 0);

        Memory memory =
                new Memory(
                        null,
                        null,
                        "original",
                        5,
                        80,
                        rememberedAt
                );

        memory.compress("summary");
        memory.compress("pattern");

        assertThat(
                policy.determine(memory, now)
        ).isEqualTo(MemoryTier.COLD);
    }

    @Test
    void recentlyAccessedColdMemoryIsPromotedToHot() {
        LocalDateTime rememberedAt =
                LocalDateTime.of(2026, 7, 1, 12, 0);

        LocalDateTime now =
                LocalDateTime.of(2026, 9, 29, 12, 0);

        Memory memory =
                new Memory(
                        null,
                        null,
                        "original",
                        5,
                        80,
                        rememberedAt
                );

        memory.compress("summary");
        memory.compress("pattern");

        memory.markAccessed(
                now.minusDays(2)
        );

        assertThat(
                policy.determine(memory, now)
        ).isEqualTo(MemoryTier.HOT);
    }
}