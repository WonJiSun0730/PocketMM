package com.example.projectmm.application.chat;

import com.example.projectmm.domain.time.AiTimeParseResult.TimePrecision;
import com.example.projectmm.domain.time.AiTimeParseResult.DurationScale;
import com.example.projectmm.domain.time.TimeInterpretation.Interpreter;
import java.time.LocalDateTime;
import java.util.List;

public record ChatDebugInfo(
        LocalDateTime serverTime,
        LocalDateTime aiContextTime,
        Interpreter interpreter,
        TimePrecision precision,
        DurationScale durationScale,
        Long expectedElapsedMinutes,
        long appliedElapsedMinutes,
        LocalDateTime previousTime,
        LocalDateTime newTime,
        String sourceExpression,
        String memoryStatus,
        List<String> savedMemories,
        List<String> recalledMemories,
        ChatStateUpdate stateUpdate
) {
}
