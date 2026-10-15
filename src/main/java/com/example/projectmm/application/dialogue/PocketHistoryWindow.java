package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.WorldRoomMessage;
import java.util.List;

/** Selects complete user-led turns; a pending current user message is additional. */
final class PocketHistoryWindow {
    private PocketHistoryWindow() {}

    static List<WorldRoomMessage> recent(List<WorldRoomMessage> chronological, int turns) {
        if (turns < 1) throw new IllegalArgumentException("turns must be positive");
        int end = chronological.size();
        if (end == 0) return List.of();
        int remaining = turns;
        // The current request is already persisted before context construction.
        if (chronological.get(end - 1).getRole() == ChatRole.USER) remaining++;
        int start = 0;
        for (int i = end - 1; i >= 0; i--) {
            if (chronological.get(i).getRole() == ChatRole.USER && --remaining == 0) {
                start = i;
                break;
            }
        }
        return List.copyOf(chronological.subList(start, end));
    }
}
