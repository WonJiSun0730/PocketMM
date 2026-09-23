package com.example.projectmm.application.memory;

import java.util.List;

public record ChatMemoryResult(String status, List<String> saved) {
    public static ChatMemoryResult empty(String status) {
        return new ChatMemoryResult(status, List.of());
    }
}
