package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.TimeChange;

import java.time.LocalDateTime;

public interface TimeInterpreter {

    TimeChange interpret(
            String userInput,
            LocalDateTime currentTime
    );
}