package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.WorldRoomMessage;
import java.util.List;

/** Each completed assistant response, including automatic continuation, is one turn. */
final class PocketHistoryWindow {
    private PocketHistoryWindow() {}

    static List<WorldRoomMessage> recent(List<WorldRoomMessage> chronological, int turns) {
        if (turns < 1) throw new IllegalArgumentException("turns must be positive");
        int completed = 0;
        int start = 0;
        for (int i = chronological.size() - 1; i >= 0; i--) {
            if (chronological.get(i).getRole() == ChatRole.CHARACTER && ++completed > turns) {
                start = i + 1;
                break;
            }
        }
        // Keep the user input belonging to the oldest selected response and any pending input.
        return List.copyOf(chronological.subList(start, chronological.size()));
    }
}
