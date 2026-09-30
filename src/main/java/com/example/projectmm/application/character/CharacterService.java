package com.example.projectmm.application.character;

import com.example.projectmm.api.character.CharacterCreateRequest;
import com.example.projectmm.api.character.CharacterResponse;
import com.example.projectmm.api.character.CharacterUpdateRequest;
import com.example.projectmm.domain.character.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.projectmm.domain.character.CharacterExample;
import com.example.projectmm.domain.character.CharacterExampleRepository;

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
    private final CharacterExampleRepository characterExampleRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public CharacterResponse create(CharacterCreateRequest request, String loginId) {

        Member owner = findMember(loginId);

        Character character =
                characterRepository.save(
                        new Character(request.name().trim(), owner)
                );

        CharacterProfile profile =
                characterProfileRepository.save(
                        new CharacterProfile(
                                character,
                                request.description(),
                                request.rawDescription(),
                                request.playGuide(),
                                request.exampleDialogue(),
                                request.personality(),
                                request.speechStyle(),
                                request.background(),
                                request.appearance()
                        )
                );

        saveExamples(
                character,
                request.examples()
        );

        characterStateRepository.save(
                new CharacterState(
                        character,
                        INITIAL_ENERGY,
                        INITIAL_MOOD,
                        LocalDateTime.now()
                )
        );

        return toResponse(character, profile);
    }

    @Transactional(readOnly = true)
    public List<CharacterResponse> getAll(String loginId) {

        return characterRepository.findAllByOwnerIdOrderByIdDesc(findMember(loginId).getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CharacterResponse getById(Long characterId, String loginId) {

        Character character =
                characterRepository.findByIdAndOwnerId(characterId, findMember(loginId).getId())
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
            CharacterUpdateRequest request,
            String loginId
    ) {
        Character character =
                characterRepository.findByIdAndOwnerId(characterId, findMember(loginId).getId())
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
                request.rawDescription(),
                request.playGuide(),
                request.exampleDialogue(),
                request.personality(),
                request.speechStyle(),
                request.background(),
                request.appearance()
        );

        replaceExamples(
                character,
                request.examples()
        );

        return toResponse(character, profile);
    }

    private Member findMember(String loginId) {
        return memberRepository.findByLoginId(loginId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Member not found. loginId=" + loginId)
                );
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
                    null, // description
                    null, // rawDescription
                    null, // playGuide
                    null, // examples
                    null, // exampleDialogue
                    null, // personality
                    null, // speechStyle
                    null, // background
                    null  // appearance
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
                profile.getRawDescription(),
                profile.getPlayGuide(),
                getExamples(character.getId()),
                profile.getExampleDialogue(),
                profile.getPersonality(),
                profile.getSpeechStyle(),
                profile.getBackground(),
                profile.getAppearance()
        );
    }

    private void saveExamples(
            Character character,
            List<String> examples
    ) {
        if (examples == null || examples.isEmpty()) {
            return;
        }

        List<String> normalizedExamples =
                examples.stream()
                        .filter(example ->
                                example != null
                                        && !example.isBlank()
                        )
                        .map(String::trim)
                        .toList();

        if (normalizedExamples.size() > 10) {
            throw new IllegalArgumentException(
                    "Character examples must not exceed 10."
            );
        }

        int totalLength =
                normalizedExamples.stream()
                        .mapToInt(String::length)
                        .sum();

        if (totalLength > 2000) {
            throw new IllegalArgumentException(
                    "Character examples must not exceed 2000 characters in total."
            );
        }

        for (int i = 0; i < normalizedExamples.size(); i++) {
            String content = normalizedExamples.get(i);

            characterExampleRepository.save(
                    new CharacterExample(
                            character,
                            content,
                            i
                    )
            );
        }
    }

    private void replaceExamples(
            Character character,
            List<String> examples
    ) {
        if (examples == null) {
            return;
        }

        characterExampleRepository.deleteByCharacterId(
                character.getId()
        );

        saveExamples(
                character,
                examples
        );
    }

    private List<String> getExamples(Long characterId) {
        return characterExampleRepository
                .findByCharacterIdOrderBySortOrderAsc(characterId)
                .stream()
                .map(CharacterExample::getContent)
                .toList();
    }
}