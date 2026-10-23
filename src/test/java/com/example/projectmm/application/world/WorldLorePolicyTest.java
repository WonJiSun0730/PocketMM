package com.example.projectmm.application.world;

import com.example.projectmm.api.world.WorldLoreItem;
import com.example.projectmm.domain.world.WorldLoreCategory;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class WorldLorePolicyTest {
    private final WorldLorePolicy policy = new WorldLorePolicy();

    @Test void oneCategoryCanUseTheWholeBudgetIncludingTitles() {
        var entries = policy.normalizeAndValidate(WorldLoreCategory.SOCIETY_CULTURE,
                List.of(new WorldLoreItem("제목", "a".repeat(9998))));
        policy.validateWorldTotal(0, entries);
        assertThatThrownBy(() -> policy.validateWorldTotal(1, entries))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10,000");
    }

    @Test void categoriesShareOneBudget() {
        var entries = policy.normalizeAndValidate(WorldLoreCategory.HISTORY,
                List.of(new WorldLoreItem(null, "a".repeat(4000))));
        policy.validateWorldTotal(6000, entries);
        assertThatThrownBy(() -> policy.validateWorldTotal(6001, entries))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void slotLimitsAndNormalizationRemain() {
        assertThat(policy.normalizeAndValidate(WorldLoreCategory.HISTORY,
                List.of(new WorldLoreItem("  제목  ", " 내용 "), new WorldLoreItem("", " "))))
                .containsExactly(new WorldLoreItem("제목", "내용"));
        assertThatThrownBy(() -> policy.normalizeAndValidate(WorldLoreCategory.SOCIETY_CULTURE,
                java.util.stream.IntStream.range(0, 11)
                        .mapToObj(i -> new WorldLoreItem(null, "x")).toList()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10");
        assertThat(policy.normalizeAndValidate(WorldLoreCategory.HISTORY, null)).isEmpty();
    }
}
