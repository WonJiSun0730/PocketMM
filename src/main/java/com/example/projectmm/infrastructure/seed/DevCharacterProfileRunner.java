package com.example.projectmm.infrastructure.seed;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterProfile;
import com.example.projectmm.domain.character.CharacterProfileRepository;
import com.example.projectmm.domain.character.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@Order(2)
@RequiredArgsConstructor
public class DevCharacterProfileRunner implements CommandLineRunner {

    private final CharacterRepository characterRepository;
    private final CharacterProfileRepository characterProfileRepository;

    @Override
    @Transactional
    public void run(String... args) {
        characterRepository.findAll().stream()
                .filter(character ->
                        !characterProfileRepository
                                .findByCharacterId(character.getId())
                                .isPresent()
                )
                .forEach(this::createEmptyProfile);
    }

    private void createEmptyProfile(Character character) {
        characterProfileRepository.save(
                new CharacterProfile(
                        character,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }
}