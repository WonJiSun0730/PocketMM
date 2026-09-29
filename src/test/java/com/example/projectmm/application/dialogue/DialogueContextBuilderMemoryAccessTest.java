package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.dialogue.DialogueMemoryContext;
import com.example.projectmm.domain.dialogue.DialogueRelationshipFactContext;
import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.memory.MemoryFact;
import com.example.projectmm.domain.relationship.RelationshipFact;
import com.example.projectmm.domain.relationship.RelationshipFactRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.projectmm.domain.character.Character;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogueContextBuilderMemoryAccessTest {

    private final DialogueContextBuilder builder =
            new DialogueContextBuilder(
                    null, // CharacterStateRepository
                    null, // CharacterProfileRepository
                    null, // ChatMessageRepository
                    null, // MemoryRecallService
                    null, // RelationshipRepository
                    null, // RelationshipFactRepository  ← 새로 추가
                    null, // ContentModePolicy
                    null, // ConversationRepository
                    null, // CharacterExampleRepository
                    null  // CustomStateValueRepository
            );

    @Test
    void implicit_기억은_구체적인_내용을_대화_AI에_노출하지_않는다() {

        EffectiveMemory memory =
                createMemory(
                        MemoryAccessLevel.IMPLICIT
                );

        DialogueMemoryContext result =
                ReflectionTestUtils.invokeMethod(
                        builder,
                        "toDialogueMemoryContext",
                        memory
                );

        assertThat(result)
                .isNotNull();

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
    void residual_기억은_구체적인_내용을_대화_AI에_노출하지_않는다() {

        EffectiveMemory memory =
                createMemory(
                        MemoryAccessLevel.RESIDUAL
                );

        DialogueMemoryContext result =
                ReflectionTestUtils.invokeMethod(
                        builder,
                        "toDialogueMemoryContext",
                        memory
                );

        assertThat(result)
                .isNotNull();

        assertThat(result.summary())
                .contains("감정적 잔향")
                .doesNotContain("비밀 사건")
                .doesNotContain("비밀 장소");

        assertThat(result.evidenceQuote())
                .isNull();

        assertThat(result.fact())
                .isNull();
    }

    @Test
    void blocked_기억이_대화_컨텍스트까지_도달하면_예외가_발생한다() {

        EffectiveMemory memory =
                createMemory(
                        MemoryAccessLevel.BLOCKED
                );

        assertThatThrownBy(
                () -> ReflectionTestUtils.invokeMethod(
                        builder,
                        "toDialogueMemoryContext",
                        memory
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "BLOCKED memory must not reach dialogue context"
                );
    }

    @Test
    void 확정된_관계_사실을_대화_Context로_변환한다() {

        RelationshipFactRepository relationshipFactRepository =
                mock(RelationshipFactRepository.class);

        DialogueContextBuilder relationshipBuilder =
                new DialogueContextBuilder(
                        null,
                        null,
                        null,
                        null,
                        null,
                        relationshipFactRepository,
                        null,
                        null,
                        null,
                        null
                );

        Character millie =
                new Character("Millie");

        Character marbas =
                new Character("Marbas");

        RelationshipFact fact =
                new RelationshipFact(
                        millie,
                        marbas,
                        "relationship_status",
                        "spouse",
                        null,
                        LocalDateTime.of(
                                2026, 2, 15,
                                12, 0
                        )
                );

        when(
                relationshipFactRepository
                        .findByFromCharacterId(
                                millie.getId()
                        )
        ).thenReturn(
                List.of(fact)
        );

        List<DialogueRelationshipFactContext> result =
                ReflectionTestUtils.invokeMethod(
                        relationshipBuilder,
                        "buildRelationshipFacts",
                        millie.getId()
                );

        assertThat(result)
                .hasSize(1);

        DialogueRelationshipFactContext context =
                result.getFirst();

        assertThat(context.targetCharacterName())
                .isEqualTo("Marbas");

        assertThat(context.relation())
                .isEqualTo("relationship_status");

        assertThat(context.value())
                .isEqualTo("spouse");
    }

    private EffectiveMemory createMemory(
            MemoryAccessLevel accessLevel
    ) {
        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 28,
                        20, 0
                );

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
                rememberedAt,
                false,
                "밀리가 비밀 장소를 방문했다.",
                fact,
                accessLevel
        );
    }
}