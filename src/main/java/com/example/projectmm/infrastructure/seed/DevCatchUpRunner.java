package com.example.projectmm.infrastructure.seed;

import com.example.projectmm.application.simulation.CatchUpExecutionService;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

//@Component
@Profile("gemini")
@Order(2)
@RequiredArgsConstructor
public class DevCatchUpRunner implements CommandLineRunner {

    private final CharacterStateRepository characterStateRepository;
    private final CatchUpExecutionService catchUpExecutionService;

    @Override
    @Transactional
    public void run(String... args) {

//        System.out.println(
//                "=== DEV GEMINI CATCH-UP RUNNER STARTED ==="
//        );

        CharacterState state =
                characterStateRepository.findAll()
                        .stream()
                        .filter(characterState ->
                                "Millie".equals(
                                        characterState
                                                .getCharacter()
                                                .getName()
                                )
                        )
                        .findFirst()
                        .orElseThrow();

        catchUpExecutionService.execute(
                state,
                LocalDateTime.now()
        );

//        System.out.println(
//                "=== DEV GEMINI CATCH-UP RUNNER FINISHED ==="
//        );
    }
}