package com.example.projectmm.api.character;

import com.example.projectmm.application.character.CharacterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CharacterResponse create(
            @Valid @RequestBody CharacterCreateRequest request,
            Authentication authentication
    ) {
        return characterService.create(request, authentication.getName());
    }

    @GetMapping
    public java.util.List<CharacterResponse> getAll(Authentication authentication) {
        return characterService.getAll(authentication.getName());
    }

    @GetMapping("/{characterId}")
    public CharacterResponse getById(
            @PathVariable Long characterId,
            Authentication authentication
    ) {
        return characterService.getById(characterId, authentication.getName());
    }

    @PutMapping("/{characterId}/profile")
    public CharacterResponse updateProfile(
            @PathVariable Long characterId,
            @RequestBody CharacterUpdateRequest request,
            Authentication authentication
    ) {
        return characterService.updateProfile(
                characterId,
                request,
                authentication.getName()
        );
    }
}