package com.example.projectmm.application.character;

import com.example.projectmm.api.character.CharacterCreateRequest;
import com.example.projectmm.api.character.CharacterResponse;
import com.example.projectmm.api.character.CharacterUpdateRequest;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterProfile;
import com.example.projectmm.domain.character.CharacterProfileRepository;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterService {

    private static final int INITIAL_ENERGY = 70;
    private static final int INITIAL_MOOD = 70;

    private final CharacterRepository characterRepository;
    private final CharacterProfileRepository characterProfileRepository;
    private final CharacterStateRepository characterStateRepository;

    @Transactional
    public CharacterResponse create(CharacterCreateRequest request) {

        Character character =
                characterRepository.save(
                        new Character(request.name().trim())
                );

        CharacterProfile profile =
                characterProfileRepository.save(
                        new CharacterProfile(
                                character,
                                request.description(),
                                request.personality(),
                                request.speechStyle(),
                                request.background(),
                                request.appearance()
                        )
                );

        characterStateRepository.save(
                new CharacterState(
                        character,
                        INITIAL_ENERGY,
                        INITIAL_MOOD,
                        LocalDateTime.now()
                )
        );

        return new CharacterResponse(
                character.getId(),
                character.getName(),
                profile.getDescription(),
                profile.getPersonality(),
                profile.getSpeechStyle(),
                profile.getBackground(),
                profile.getAppearance()
        );
    }

    @Transactional(readOnly = true)
    public List<CharacterResponse> getAll() {

        return characterRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CharacterResponse getById(Long characterId) {

        Character character =
                characterRepository.findById(characterId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Character not found. characterId="
                                                + characterId
                                )
                        );

        return toResponse(character);
    }

    @Transactional
    public CharacterResponse updateProfile(
            Long characterId,
            CharacterUpdateRequest request
    ) {
        Character character =
                characterRepository.findById(characterId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Character not found. characterId="
                                                + characterId
                                )
                        );

        CharacterProfile profile =
                characterProfileRepository
                        .findByCharacterId(characterId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Character profile not found. characterId="
                                                + characterId
                                )
                        );

        profile.update(
                request.description(),
                request.personality(),
                request.speechStyle(),
                request.background(),
                request.appearance()
        );

        return toResponse(character, profile);
    }

    private CharacterResponse toResponse(Character character) {

        CharacterProfile profile =
                characterProfileRepository
                        .findByCharacterId(character.getId())
                        .orElse(null);

        if (profile == null) {
            return new CharacterResponse(
                    character.getId(),
                    character.getName(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return toResponse(character, profile);
    }

    private CharacterResponse toResponse(
            Character character,
            CharacterProfile profile
    ) {
        return new CharacterResponse(
                character.getId(),
                character.getName(),
                profile.getDescription(),
                profile.getPersonality(),
                profile.getSpeechStyle(),
                profile.getBackground(),
                profile.getAppearance()
        );
    }
}