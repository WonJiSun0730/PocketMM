package com.example.projectmm.application.world;

import com.example.projectmm.domain.world.WorldLoreCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldLorePolicyTest {

    private final WorldLorePolicy policy = new WorldLorePolicy();

    @Test
    void limitsAreSharedAcrossSlotsInsteadOfAppliedPerSlot() {
        var entries = List.of("a".repeat(1200), "b".repeat(800));

        assertThat(policy.normalizeAndValidate(
                WorldLoreCategory.ERA_REGION_BACKGROUND,
                entries
        )).hasSize(2);

        assertThatThrownBy(() -> policy.normalizeAndValidate(
                WorldLoreCategory.ERA_REGION_BACKGROUND,
                List.of("a".repeat(1200), "b".repeat(801))
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("2000 characters in total");
    }

    @Test
    void societyCultureAllowsAtMostTenSlotsSharingFiveHundredCharacters() {
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                WorldLoreCategory.SOCIETY_CULTURE,
                java.util.stream.IntStream.range(0, 11)
                        .mapToObj(i -> "x")
                        .toList()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("10");
    }

    @Test
    void historyAllowsAtMostTenSlotsSharingOneThousandCharacters() {
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                WorldLoreCategory.HISTORY,
                List.of("a".repeat(600), "b".repeat(401))
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("1000 characters in total");
    }
}
