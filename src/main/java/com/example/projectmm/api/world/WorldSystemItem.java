package com.example.projectmm.api.world;

import java.util.List;

public record WorldSystemItem(
        String name,
        String changeCondition,
        int minValue,
        int maxValue,
        @jakarta.validation.constraints.NotNull(message = "시스템 초기값을 입력해주세요.") Integer initialValue,
        List<WorldSystemStateItem> states
) {
}
