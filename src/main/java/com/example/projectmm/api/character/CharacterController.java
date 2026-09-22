package com.example.projectmm.api.character;

import com.example.projectmm.application.character.CharacterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CharacterResponse create(
            @Valid @RequestBody CharacterCreateRequest request
    ) {
        return characterService.create(request);
    }

    @GetMapping
    public java.util.List<CharacterResponse> getAll() {
        return characterService.getAll();
    }

    @GetMapping("/{characterId}")
    public CharacterResponse getById(
            @PathVariable Long characterId
    ) {
        return characterService.getById(characterId);
    }

    @PutMapping("/{characterId}/profile")
    public CharacterResponse updateProfile(
            @PathVariable Long characterId,
            @RequestBody CharacterUpdateRequest request
    ) {
        return characterService.updateProfile(
                characterId,
                request
        );
    }
}