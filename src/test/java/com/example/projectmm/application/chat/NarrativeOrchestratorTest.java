package com.example.projectmm.application.chat;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.Conversation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NarrativeOrchestratorTest {

    @Test
    void liveDialogueWithoutCatchUpUsesDirectEntry() {
        var progressService =
                mock(ConversationProgressService.class);

        var orchestrator =
                new NarrativeOrchestrator(progressService);

        var character =
                new Character("테스트 캐릭터");

        var room =
                Conversation.fresh(
                        character,
                        LocalDateTime.of(
                                2030,
                                1,
                                1,
                                12,
                                0
                        )
                );

        when(progressService.beginChat(1L, 2L))
                .thenReturn(
                        new ConversationProgressService.ChatOperation(
                                room,
                                "token",
                                NarrativeEntry.DIRECT
                        )
                );

        var operation =
                orchestrator.beginLiveDialogue(
                        1L,
                        2L
                );

        assertThat(operation.mode())
                .isEqualTo(NarrativeMode.LIVE_DIALOGUE);

        assertThat(operation.entry())
                .isEqualTo(NarrativeEntry.DIRECT);

        assertThat(operation.room())
                .isSameAs(room);

        assertThat(operation.token())
                .isEqualTo("token");
    }

    @Test
    void liveDialogueAfterCatchUpUsesAfterCatchUpEntry() {
        var progressService =
                mock(ConversationProgressService.class);

        var orchestrator =
                new NarrativeOrchestrator(progressService);

        var character =
                new Character("테스트 캐릭터");

        var room =
                Conversation.fresh(
                        character,
                        LocalDateTime.of(
                                2030,
                                1,
                                1,
                                12,
                                0
                        )
                );

        when(progressService.beginChat(1L, 2L))
                .thenReturn(
                        new ConversationProgressService.ChatOperation(
                                room,
                                "token",
                                NarrativeEntry.AFTER_CATCH_UP
                        )
                );

        var operation =
                orchestrator.beginLiveDialogue(
                        1L,
                        2L
                );

        assertThat(operation.mode())
                .isEqualTo(NarrativeMode.LIVE_DIALOGUE);

        assertThat(operation.entry())
                .isEqualTo(NarrativeEntry.AFTER_CATCH_UP);
    }

    @Test
    void catchUp이후에도_대화실행계약을_그대로_보존한다() {
        var progressService =
                mock(ConversationProgressService.class);

        var orchestrator =
                new NarrativeOrchestrator(progressService);

        var character =
                new Character("테스트 캐릭터");

        var room =
                Conversation.fresh(
                        character,
                        LocalDateTime.of(
                                2030,
                                1,
                                1,
                                12,
                                0
                        )
                );

        when(progressService.beginChat(10L, 20L))
                .thenReturn(
                        new ConversationProgressService.ChatOperation(
                                room,
                                "catch-up-token",
                                NarrativeEntry.AFTER_CATCH_UP
                        )
                );

        var operation =
                orchestrator.beginLiveDialogue(
                        10L,
                        20L
                );

        assertThat(operation.room())
                .isSameAs(room);

        assertThat(operation.token())
                .isEqualTo("catch-up-token");

        assertThat(operation.mode())
                .isEqualTo(NarrativeMode.LIVE_DIALOGUE);

        assertThat(operation.entry())
                .isEqualTo(NarrativeEntry.AFTER_CATCH_UP);
    }
}