package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.memory.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DataJpaTest(
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        },
        showSql = false
)
@Import(MemoryProvenanceAccessPolicy.class)
class MemoryConversationScenarioIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private MemoryAccessStateRepository accessRepository;

    @Autowired
    private MemoryProvenanceAccessPolicy provenanceAccess;

    private MemoryLifecycleService lifecycleService;
    private MemoryRecallService recallService;

    private Character character;
    private Conversation conversation;

    private final LocalDateTime now =
            LocalDateTime.of(
                    2026, 9, 29,
                    12, 0
            );

    @BeforeEach
    void setUp() {
        character =
                new Character("Millie");

        em.persist(character);

        conversation =
                Conversation.fresh(
                        character,
                        now.minusDays(10)
                );

        em.persist(conversation);

        lifecycleService =
                mock(MemoryLifecycleService.class);

        recallService =
                new MemoryRecallService(
                        memoryRepository,
                        new MemoryDecayPolicy(),
                        mock(MemoryCompressionService.class),
                        mock(CharacterStateRepository.class),
                        accessRepository,
                        lifecycleService,
                        provenanceAccess
                );
    }

    @Test
    void 시나리오_BLOCKED와_미래기억을_제외하고_실제_회상된_기억만_accessed_처리한다() {

        Memory target =
                conversationMemory(
                        "나는 오리고기를 가장 좋아해",
                        8,
                        now.minusDays(3)
                );

        Memory unrelated =
                conversationMemory(
                        "제주도에서 지갑을 잃어버렸어",
                        8,
                        now.minusDays(2)
                );

        Memory blocked =
                conversationMemory(
                        "비밀 장소를 방문했어",
                        10,
                        now.minusDays(1)
                );

        Memory future =
                conversationMemory(
                        "내일 부산에 도착했어",
                        10,
                        now.plusDays(1)
                );

        em.flush();

        new MemoryAccessService(
                memoryRepository,
                accessRepository,
                mock(MemoryLifecycleService.class)
        ).changeAccess(
                blocked.getId(),
                MemoryAccessLevel.BLOCKED,
                now
        );

        em.flush();
        em.clear();

        List<EffectiveMemory> result =
                recallService.recallForConversation(
                        character.getId(),
                        conversation.getId(),
                        now,
                        1,
                        "내가 가장 좋아하는 음식이 뭐였지?"
                );

        assertThat(result)
                .hasSize(1);

        assertThat(result.get(0).memoryId())
                .isEqualTo(target.getId());

        assertThat(result.get(0).summary())
                .contains("오리고기")
                .doesNotContain("비밀 장소")
                .doesNotContain("부산");

        verify(lifecycleService)
                .markAccessed(
                        List.of(
                                target.getId()
                        ),
                        now
                );

        verify(lifecycleService, never())
                .markAccessed(
                        argThat(ids ->
                                ids.contains(
                                        unrelated.getId()
                                )
                                        || ids.contains(
                                        blocked.getId()
                                )
                                        || ids.contains(
                                        future.getId()
                                )
                        ),
                        eq(now)
                );
    }

    private Memory conversationMemory(
            String summary,
            int importance,
            LocalDateTime rememberedAt
    ) {
        Memory memory =
                new Memory(
                        character,
                        null,
                        summary,
                        importance,
                        importance * 10,
                        rememberedAt
                );

        ReflectionTestUtils.setField(
                memory,
                "conversation",
                conversation
        );

        em.persist(memory);

        return memory;
    }
}