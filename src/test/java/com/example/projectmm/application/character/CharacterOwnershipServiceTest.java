package com.example.projectmm.application.character;

import com.example.projectmm.api.character.CharacterCreateRequest;
import com.example.projectmm.api.character.CharacterUpdateRequest;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.projectmm.application.dialogue.DialogueGateway;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:character-ownership;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class CharacterOwnershipServiceTest {

    @Autowired CharacterService characterService;
    @Autowired CharacterRepository characterRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired com.example.projectmm.domain.world.WorldRepository worlds;
    @MockitoBean DialogueGateway dialogueGateway;

    @Test
    void characterLibraryIsIsolatedByOwner() {
        Member owner = saveMember("owner1", "owner1@pocketmm.test");
        Member other = saveMember("owner2", "owner2@pocketmm.test");

        var created = characterService.create(request("Millie"), owner.getLoginId());

        assertThat(characterService.getAll(owner.getLoginId()))
                .extracting("id")
                .containsExactly(created.id());

        assertThat(characterService.getAll(other.getLoginId())).isEmpty();

        assertThatThrownBy(() -> characterService.getById(created.id(), other.getLoginId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Character not found");
    }

    @Test
    void createdCharacterPersistsAuthenticatedOwner() {
        Member owner = saveMember("owner3", "owner3@pocketmm.test");

        var created = characterService.create(request("Marbas"), owner.getLoginId());

        var saved = characterRepository.findById(created.id()).orElseThrow();
        assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
    }

    private Member saveMember(String loginId, String email) {
        return memberRepository.save(
                Member.createLocal(loginId, "password123", loginId, email, passwordEncoder)
        );
    }

    @Test
    void libraryDetailAndProfileUpdateRetainExistingWorldSelection() {
        Member owner = saveMember("world-owner", "world-owner@pocketmm.test");
        var world = worlds.save(new com.example.projectmm.domain.world.World(owner, "TestWorld", ""));
        var created = characterService.create(request("Marbas"), owner.getLoginId());
        var character = characterRepository.findById(created.id()).orElseThrow();
        character.assignWorld(world);
        characterRepository.flush();

        assertThat(characterService.getAll(owner.getLoginId()).getFirst().worldId()).isEqualTo(world.getId());
        assertThat(characterService.getById(created.id(), owner.getLoginId()).worldId()).isEqualTo(world.getId());
        var updated = characterService.updateProfile(created.id(), new CharacterUpdateRequest(
                "Counsellor", null, null, null, null, "British English", null, null, null, null), owner.getLoginId());
        assertThat(updated.worldId()).isEqualTo(world.getId());
        assertThat(character.getWorld().getId()).isEqualTo(world.getId());
        assertThat(new tools.jackson.databind.ObjectMapper().writeValueAsString(updated))
                .contains("\"worldId\":" + world.getId());
    }

    @Test
    void missingProfileStillReturnsWorldSelectionAndUnassignedCharacterReturnsNull() {
        Member owner = saveMember("legacy-world-owner", "legacy-world-owner@pocketmm.test");
        var world = worlds.save(new com.example.projectmm.domain.world.World(owner, "Legacy World", ""));
        var legacy = characterRepository.save(new com.example.projectmm.domain.character.Character("Legacy", owner));
        legacy.assignWorld(world);
        assertThat(characterService.getById(legacy.getId(), owner.getLoginId()).worldId()).isEqualTo(world.getId());
        var created = characterService.create(request("Unassigned"), owner.getLoginId());
        assertThat(created.worldId()).isNull();
        assertThat(characterService.getById(created.id(), owner.getLoginId()).worldId()).isNull();
    }

    private CharacterCreateRequest request(String name) {
        return new CharacterCreateRequest(
                name,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }
}
