package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.*;
import com.example.projectmm.application.memory.*;
import com.example.projectmm.application.policy.*;
import com.example.projectmm.application.state.CustomStateProposalService;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.domain.state.CustomStateChangeProposal;
import com.example.projectmm.domain.time.TimeInterpretation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceCustomStateTest {
    @Mock CharacterRepository characters;
    @Mock CharacterProfileRepository profiles;
    @Mock ConversationRepository rooms;
    @Mock ChatMessageRepository messages;
    @Mock DialogueContextBuilder contexts;
    @Mock DialogueGateway gateway;
    @Mock ContentModePolicy policy;
    @Mock TimeInterpreter time;
    @Mock ChatMemoryService memories;
    @Mock ConversationProgressService progress;
    @Mock NarrativeOrchestrator narrativeOrchestrator;
    @InjectMocks ChatService chat;

    @Test void passesWholeTurnToCompletionAndReturnsReplyEvenWhenCustomProposalRejected() {
        var at = LocalDateTime.of(2030, 1, 1, 12, 0);
        var character = new Character("NPC");
        ReflectionTestUtils.setField(character, "id", 1L);
        var room = Conversation.fresh(character, at);
        ReflectionTestUtils.setField(room, "id", 2L);
        var context = new DialogueContext(
                1L, "NPC", null,
                70, 70,
                at,
                ContentMode.NORMAL,
                List.of(), List.of(), List.of(),
                null,
                70, 70,
                List.of()
        );
        var turn = new DialogueTurn("안녕하세요", DialogueStateChange.none(),
                List.of(new CustomStateChangeProposal(3L, -5.0)));
        var source = new ChatMessage(room, character, ChatRole.USER, "안녕", ContentMode.NORMAL, at);
        when(characters.findById(1L)).thenReturn(Optional.of(character));
        when(rooms.findTopByCharacterIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(Optional.of(room));
        when(narrativeOrchestrator.beginLiveDialogue(1L, 2L))
                .thenReturn(
                        new NarrativeOrchestrator.NarrativeOperation(
                                room,
                                "token",
                                NarrativeMode.LIVE_DIALOGUE,
                                NarrativeEntry.DIRECT
                        )
                );
        when(time.interpret("안녕", at)).thenReturn(TimeInterpretation.noMatch());
        when(policy.resolve(ContentMode.NORMAL)).thenReturn(ContentMode.NORMAL);
        when(contexts.build(1L, 2L, ContentMode.NORMAL, "안녕")).thenReturn(context);
        when(gateway.generateTurn(context, "안녕")).thenReturn(turn);
        when(progress.completeChat(1L, 2L, "token", "안녕", turn, ContentMode.NORMAL))
                .thenReturn(new ConversationProgressService.CompletedChat(
                        source,
                        ChatStateUpdate.resolve(
                                70, 70,
                                turn.reply(),
                                turn.stateChange()
                        ),
                        PlayerStateUpdate.resolve(
                                70, 70,
                                "안녕",
                                turn.playerStateChange()
                        ),
                        CustomStateProposalService.ApplyStatus.REJECTED
                ));
        when(memories.remember(room, source, at)).thenReturn(ChatMemoryResult.empty("NONE"));

        assertThat(chat.chat(1L, new ChatRequest("안녕", ContentMode.NORMAL)).message()).isEqualTo("안녕하세요");
        // orchestration은 제안 목록을 보존하여 넘기고, 저장/적용은 완료 서비스에만 맡긴다.
        verify(progress).completeChat(1L, 2L, "token", "안녕", turn, ContentMode.NORMAL);
        verify(progress).endChat(1L, 2L, "token");
        verifyNoInteractions(messages);
    }

    @Test
    void continuationSkipsUserMemoryCapture() {
        var at = LocalDateTime.of(2030, 1, 1, 12, 0);

        var character = new Character("NPC");
        ReflectionTestUtils.setField(character, "id", 1L);

        var room = Conversation.fresh(character, at);
        ReflectionTestUtils.setField(room, "id", 2L);

        var context = new DialogueContext(
                1L,
                "NPC",
                null,
                70,
                70,
                at,
                ContentMode.NORMAL,
                List.of(),
                List.of(),
                List.of(),
                null,
                70,
                70,
                List.of()
        );

        var turn =
                new DialogueTurn(
                        "*잠시 생각하다가 다시 입을 열었다.*",
                        DialogueStateChange.none()
                );

        when(characters.findById(1L))
                .thenReturn(Optional.of(character));

        when(rooms.findTopByCharacterIdOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(Optional.of(room));

        when(narrativeOrchestrator.beginLiveDialogue(1L, 2L))
                .thenReturn(
                        new NarrativeOrchestrator.NarrativeOperation(
                                room,
                                "token",
                                NarrativeMode.LIVE_DIALOGUE,
                                NarrativeEntry.DIRECT
                        )
                );

        when(policy.resolve(ContentMode.NORMAL))
                .thenReturn(ContentMode.NORMAL);

        when(contexts.build(1L, 2L, ContentMode.NORMAL, ""))
                .thenReturn(context);

        when(gateway.generateTurn(context, ""))
                .thenReturn(turn);

        when(progress.completeChat(
                1L,
                2L,
                "token",
                "",
                turn,
                ContentMode.NORMAL
        )).thenReturn(
                new ConversationProgressService.CompletedChat(
                        null,
                        ChatStateUpdate.resolve(
                                70,
                                70,
                                turn.reply(),
                                turn.stateChange()
                        ),
                        PlayerStateUpdate.resolve(
                                70,
                                70,
                                "",
                                turn.playerStateChange()
                        ),
                        CustomStateProposalService.ApplyStatus.NONE
                )
        );

        var response =
                chat.chat(
                        1L,
                        new ChatRequest("", ContentMode.NORMAL)
                );

        assertThat(response.message())
                .isEqualTo("*잠시 생각하다가 다시 입을 열었다.*");

        verify(memories, never())
                .remember(any(), any(), any());

        verify(time, never())
                .interpret(anyString(), any());

        verify(progress)
                .completeChat(
                        1L,
                        2L,
                        "token",
                        "",
                        turn,
                        ContentMode.NORMAL
                );
    }
}
