package com.example.projectmm.domain.state;

import com.example.projectmm.domain.player.PlayerProfile;
import com.example.projectmm.domain.player.PlayerProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
class CustomStateDefinitionRepositoryTest {
    @Autowired CustomStateDefinitionRepository definitions;
    @Autowired PlayerProfileRepository profiles;
    @Autowired EntityManager em;

    @Test
    void persistsDefinitionsByOwnerAndReloadsEditedDefinition() {
        var first = profiles.save(new PlayerProfile(1, "First", "Description", null));
        var second = profiles.save(new PlayerProfile(2, "Second", "Description", null));
        var empty = profiles.save(new PlayerProfile(3, "Empty", "Description", null));
        var pieta = definitions.save(new CustomStateDefinition(first, "피에타", 0, 100, 100, "%", "신앙"));
        var mana = definitions.save(new CustomStateDefinition(first, "마력", -10.5, 200.5, 50.5, null, null));
        var other = definitions.save(new CustomStateDefinition(second, "피에타", 0, 10, 5, "%", "별도 정의"));
        em.flush();
        em.clear();

        var owned = definitions.findByPlayerProfileIdOrderByIdAsc(first.getId());
        assertThat(owned).extracting(CustomStateDefinition::getId).containsExactly(pieta.getId(), mana.getId());
        assertThat(definitions.findByPlayerProfileIdOrderByIdAsc(second.getId()))
                .extracting(CustomStateDefinition::getId).containsExactly(other.getId());
        assertThat(definitions.findByPlayerProfileIdOrderByIdAsc(empty.getId())).isEmpty();
        var loaded = owned.getFirst();
        assertThat(loaded.getPlayerProfile().getId()).isEqualTo(first.getId());
        assertThat(loaded.getName()).isEqualTo("피에타");
        assertThat(loaded.getDefaultValue()).isEqualTo(100);
        assertThat(loaded.getUnit()).isEqualTo("%");
        assertThat(loaded.getDescription()).isEqualTo("신앙");
        assertThat(owned.get(1).getMinValue()).isEqualTo(-10.5);
        assertThat(owned.get(1).getMaxValue()).isEqualTo(200.5);
        assertThat(owned.get(1).getDefaultValue()).isEqualTo(50.5);

        loaded.update("피에타 수정", -5, 120, 80, "점", "수정된 정의");
        em.flush();
        em.clear();
        var updated = definitions.findById(pieta.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("피에타 수정");
        assertThat(updated.getMinValue()).isEqualTo(-5);
        assertThat(updated.getMaxValue()).isEqualTo(120);
        assertThat(updated.getDefaultValue()).isEqualTo(80);
        assertThat(updated.getUnit()).isEqualTo("점");
        assertThat(updated.getDescription()).isEqualTo("수정된 정의");
    }
}
