package com.example.projectmm.application.dialogue;

import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.policy.ContentModePolicy;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.memory.MemoryFact;
import com.example.projectmm.domain.memory.MemoryTier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DataJpaTest(
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        },
        showSql = false
)
@Import({
        DialogueContextBuilder.class,
        ContentModePolicy.class
})
class DialogueMemoryAccessScenarioIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private DialogueContextBuilder contextBuilder;

    @MockitoBean
    private MemoryRecallService memoryRecallService;

    private final LocalDateTime now =
            LocalDateTime.of(
                    2026, 9, 29,
                    12, 0
            );

    @Test
    void 시나리오_IMPLICIT_기억은_최종_대화컨텍스트에서_구체정보를_노출하지_않는다() {

        var room =
                persistRoom();

        whenRecallReturns(
                memory(
                        MemoryAccessLevel.IMPLICIT
                )
        );

        var context =
                contextBuilder.build(
                        room.getCharacter().getId(),
                        room.getId(),
                        ContentMode.NORMAL,
                        "그 장소 기억나요?"
                );

        assertThat(context.memories())
                .hasSize(1);

        var result =
                context.memories().get(0);

        assertThat(result.summary())
                .contains("익숙함")
                .doesNotContain("비밀 사건")
                .doesNotContain("비밀 장소");

        assertThat(result.evidenceQuote())
                .isNull();

        assertThat(result.fact())
                .isNull();
    }

    @Test
    void 시나리오_RESIDUAL_기억은_최종_대화컨텍스트에서_감정잔향만_남긴다() {

        var room =
                persistRoom();

        whenRecallReturns(
                memory(
                        MemoryAccessLevel.RESIDUAL
                )
        );

        var context =
                contextBuilder.build(
                        room.getCharacter().getId(),
                        room.getId(),
                        ContentMode.NORMAL,
                        "그 장소 기억나요?"
                );

        assertThat(context.memories())
                .hasSize(1);

        var result =
                context.memories().get(0);

        assertThat(result.summary())
                .contains("감정적 잔향")
                .doesNotContain("비밀 사건")
                .doesNotContain("비밀 장소");

        assertThat(result.evidenceQuote())
                .isNull();

        assertThat(result.fact())
                .isNull();
    }

    private Conversation persistRoom() {
        Character character =
                new Character("Millie");

        em.persist(character);

        em.persist(
                new CharacterState(
                        character,
                        70,
                        70,
                        now
                )
        );

        Conversation room =
                Conversation.fresh(
                        character,
                        now
                );

        em.persist(room);
        em.flush();

        return room;
    }

    private void whenRecallReturns(
            EffectiveMemory memory
    ) {
        when(
                memoryRecallService.recallForConversation(
                        anyLong(),
                        anyLong(),
                        any(LocalDateTime.class),
                        anyInt(),
                        anyString(),
                        anyBoolean()
                )
        ).thenReturn(
                List.of(memory)
        );
    }

    private EffectiveMemory memory(
            MemoryAccessLevel accessLevel
    ) {
        MemoryFact fact =
                new MemoryFact(
                        MemoryFact.Kind.REPORTED_EVENT,
                        "밀리",
                        "방문했다",
                        "비밀 장소"
                );

        return new EffectiveMemory(
                100L,
                "밀리가 비밀 사건을 기억하고 있다.",
                9,
                90,
                now.minusDays(1),
                false,
                "밀리가 비밀 장소를 방문했다.",
                fact,
                null,
                accessLevel,
                MemoryTier.HOT
        );
    }
}