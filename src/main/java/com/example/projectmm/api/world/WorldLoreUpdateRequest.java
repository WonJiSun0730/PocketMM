package com.example.projectmm.api.world;

import java.util.List;

public record WorldLoreUpdateRequest(
        List<WorldLoreItem> entries
) {
}
