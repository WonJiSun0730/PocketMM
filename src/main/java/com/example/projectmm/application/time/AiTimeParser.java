package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.AiTimeParseResult;

import java.time.LocalDateTime;

public interface AiTimeParser {

    AiTimeParseResult parse(
            String userInput,
            LocalDateTime currentTime
    );
}