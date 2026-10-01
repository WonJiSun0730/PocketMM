package com.example.projectmm.domain.dialogue;

import java.util.List;

public record WorldRoomSystemContext(
        Long systemId,
        String name,
        int currentValue,
        int minValue,
        int maxValue,
        String changeCondition,
        List<DialogueWorldSystemState> states
) {
}
