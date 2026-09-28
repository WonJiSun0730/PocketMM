package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.dialogue.DialogueMemoryContext;
import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.memory.MemoryFact;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DialogueContextBuilderMemoryAccessTest {

    private final DialogueContextBuilder builder =
            new DialogueContextBuilder(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
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