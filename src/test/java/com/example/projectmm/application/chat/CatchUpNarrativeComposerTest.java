package com.example.projectmm.application.chat;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.simulation.EventSource;
import com.example.projectmm.domain.simulation.ResolvedEvent;
import com.example.projectmm.domain.simulation.StateDelta;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatchUpNarrativeComposerTest {

    @Test
    void 사건을_시간순으로_정렬해서_narrative_gateway에_전달한다() {

        CatchUpNarrativeGateway gateway =
                mock(CatchUpNarrativeGateway.class);

        CatchUpNarrativeComposer composer =
                new CatchUpNarrativeComposer(
                        gateway
                );

        Character character =
                new Character("Millie");

        ResolvedEvent first =
                event(
                        LocalDateTime.of(
                                2026, 9, 21,
                                14, 0
                        ),
                        "친구에게 연락해 안부를 물었다."
                );

        ResolvedEvent second =
                event(
                        LocalDateTime.of(
                                2026, 9, 21,
                                18, 0
                        ),
                        "친구와 저녁 약속을 잡았다."
                );

        when(gateway.narrate(
                any(Character.class),
                anyList()
        )).thenAnswer(invocation -> {

            List<ResolvedEvent> received =
                    invocation.getArgument(1);

            assertThat(received)
                    .containsExactly(
                            first,
                            second
                    );

            return "밀리는 친구에게 연락한 뒤 저녁 약속을 잡았다.";
        });

        String narrative =
                composer.compose(
                        character,
                        List.of(
                                second,
                                first
                        )
                );

        assertThat(narrative)
                .isEqualTo(
                        "밀리는 친구에게 연락한 뒤 저녁 약속을 잡았다."
                );
    }

    @Test
    void narrative_gateway가_실패하면_시간순_summary로_fallback한다() {

        CatchUpNarrativeGateway gateway =
                mock(CatchUpNarrativeGateway.class);

        CatchUpNarrativeComposer composer =
                new CatchUpNarrativeComposer(
                        gateway
                );

        when(gateway.narrate(
                any(Character.class),
                anyList()
        )).thenThrow(
                new IllegalStateException(
                        "Gemini unavailable"
                )
        );

        ResolvedEvent first =
                event(
                        LocalDateTime.of(
                                2026, 9, 21,
                                14, 0
                        ),
                        "친구에게 연락해 안부를 물었다."
                );

        ResolvedEvent second =
                event(
                        LocalDateTime.of(
                                2026, 9, 21,
                                18, 0
                        ),
                        "친구와 저녁 약속을 잡았다."
                );

        String narrative =
                composer.compose(
                        new Character("Millie"),
                        List.of(
                                second,
                                first
                        )
                );

        assertThat(narrative)
                .isEqualTo(
                        "친구에게 연락해 안부를 물었다. "
                                + "친구와 저녁 약속을 잡았다."
                );
    }

    @Test
    void 사건이_없으면_gateway를_호출하지_않고_빈_서술을_반환한다() {

        CatchUpNarrativeGateway gateway =
                mock(CatchUpNarrativeGateway.class);

        CatchUpNarrativeComposer composer =
                new CatchUpNarrativeComposer(
                        gateway
                );

        String narrative =
                composer.compose(
                        new Character("Millie"),
                        List.of()
                );

        assertThat(narrative)
                .isEmpty();
    }

    private ResolvedEvent event(
            LocalDateTime occurredAt,
            String summary
    ) {
        return new ResolvedEvent(
                occurredAt,
                "DAILY_ACTIVITY",
                summary,
                5,
                StateDelta.none(),
                null,
                null,
                EventSource.AI
        );
    }
}