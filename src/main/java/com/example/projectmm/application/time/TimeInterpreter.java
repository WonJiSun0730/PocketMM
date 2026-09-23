package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.TimeInterpretation;

import java.time.LocalDateTime;

public interface TimeInterpreter {

    TimeInterpretation interpret(
            String userInput,
            LocalDateTime currentTime
    );
}