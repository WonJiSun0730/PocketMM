package com.example.projectmm.application.world;

import com.example.projectmm.api.world.WorldCreateRequest;
import com.example.projectmm.api.world.WorldLoreItem;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.world.WorldLoreCategory;
import com.example.projectmm.domain.world.WorldLoreEntryRepository;
import com.example.projectmm.domain.world.WorldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({WorldService.class, WorldLorePolicy.class, WorldServiceTest.TestConfig.class})
class WorldServiceTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired WorldService worldService;
    @Autowired WorldRepository worldRepository;
    @Autowired WorldLoreEntryRepository worldLoreEntryRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void worldLibraryIsIsolatedByOwner() {
        Member owner = saveMember("worldowner1", "worldowner1@pocketmm.test");
        Member other = saveMember("worldowner2", "worldowner2@pocketmm.test");

        var created = worldService.create(
                new WorldCreateRequest("QWL", "하계의 기관과 인물들이 존재하는 세계"),
                owner.getLoginId()
        );

        assertThat(worldService.getAll(owner.getLoginId()))
                .extracting("id")
                .containsExactly(created.id());
        assertThat(worldService.getAll(other.getLoginId())).isEmpty();

        assertThatThrownBy(() -> worldService.getById(created.id(), other.getLoginId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("World not found");
    }

    @Test
    void loreSlotsPersistInOrderAndShareWorldCharacterLimit() {
        Member owner = saveMember("worldowner3", "worldowner3@pocketmm.test");
        var world = worldService.create(
                new WorldCreateRequest("QWL", null),
                owner.getLoginId()
        );

        var saved = worldService.replaceLore(
                world.id(),
                WorldLoreCategory.ERA_REGION_BACKGROUND,
                List.of(new WorldLoreItem(null, "a".repeat(1200)), new WorldLoreItem(null, "b".repeat(800))),
                owner.getLoginId()
        );

        assertThat(saved.entries()).extracting(WorldLoreItem::content).containsExactly("a".repeat(1200), "b".repeat(800));
        assertThat(saved.totalCharacters()).isEqualTo(2000);
        assertThat(saved.maxTotalCharacters()).isEqualTo(10000);
        assertThat(saved.maxEntries()).isEqualTo(20);

        assertThat(worldLoreEntryRepository
                .findByWorldIdAndCategoryOrderBySortOrderAsc(
                        world.id(),
                        WorldLoreCategory.ERA_REGION_BACKGROUND
                ))
                .extracting("content")
                .containsExactly("a".repeat(1200), "b".repeat(800));

        var history = worldService.replaceLore(world.id(), WorldLoreCategory.HISTORY,
                List.of(new WorldLoreItem(null, "h".repeat(8000))), owner.getLoginId());
        assertThat(history.worldTotalCharacters()).isEqualTo(10000);
        // Replacing a category must exclude its previous contents from the budget.
        assertThat(worldService.replaceLore(world.id(), WorldLoreCategory.ERA_REGION_BACKGROUND,
                saved.entries(), owner.getLoginId()).worldTotalCharacters()).isEqualTo(10000);

        assertThatThrownBy(() -> worldService.replaceLore(
                world.id(),
                WorldLoreCategory.ERA_REGION_BACKGROUND,
                List.of(new WorldLoreItem(null, "a".repeat(1200)), new WorldLoreItem(null, "b".repeat(801))),
                owner.getLoginId()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10,000");
    }

    @Test
    void anotherOwnerCannotReadOrReplaceLore() {
        Member owner = saveMember("worldowner4", "worldowner4@pocketmm.test");
        Member other = saveMember("worldowner5", "worldowner5@pocketmm.test");
        var world = worldService.create(
                new WorldCreateRequest("Private World", null),
                owner.getLoginId()
        );

        assertThatThrownBy(() -> worldService.getLore(
                world.id(),
                WorldLoreCategory.WORLD_RULE,
                other.getLoginId()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("World not found");

        assertThatThrownBy(() -> worldService.replaceLore(
                world.id(),
                WorldLoreCategory.WORLD_RULE,
                List.of(new WorldLoreItem(null, "침입자가 수정하면 안 되는 규칙")),
                other.getLoginId()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("World not found");
    }

    private Member saveMember(String loginId, String email) {
        return memberRepository.save(
                Member.createLocal(loginId, "password123", loginId, email, passwordEncoder)
        );
    }
}
