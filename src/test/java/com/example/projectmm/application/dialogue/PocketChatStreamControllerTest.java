package com.example.projectmm.application.dialogue;

import com.example.projectmm.api.world.PocketChatStreamController;
import com.example.projectmm.api.world.WorldRoomChatRequest;
import com.example.projectmm.application.chat.WorldRoomDialogueService;
import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.domain.chat.WorldRoomSession;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PocketChatStreamControllerTest {
    final WorldRoomSessionService sessions=mock(WorldRoomSessionService.class);
    final WorldRoomDialogueService dialogue=mock(WorldRoomDialogueService.class);
    final Authentication auth=mock(Authentication.class);
    final PocketChatStreamController controller=new PocketChatStreamController(sessions,dialogue);

    void owned(long worldId) {
        when(auth.getName()).thenReturn("owner");
        var session=mock(WorldRoomSession.class,RETURNS_DEEP_STUBS);
        when(session.getRoom().getWorld().getId()).thenReturn(worldId);
        when(sessions.ownedSession(2L,3L,"owner")).thenReturn(session);
    }
    @Test void flushesProvisionalBodyBeforeServiceAndMemoryCompletion() throws Exception {
        owned(1L);
        var response=new MockHttpServletResponse();
        when(dialogue.chat(3L,"hello")).thenAnswer(call->{
            DialogueTokens.generate(()->{DialogueTokens.publish("미확정");return null;});
            assertThat(response.getContentAsString()).contains("delta").doesNotContain("preview","complete");
            DialoguePreview.generated("미확정 본문");
            assertThat(response.isCommitted()).isTrue();
            assertThat(response.getContentAsString()).contains("미확정 본문").doesNotContain("complete");
            return new WorldRoomTurn("검증된 수정본",WorldRoomCastChange.none());
        });
        controller.chat(1L,2L,3L,new WorldRoomChatRequest("hello"),auth,response);
        assertThat(response.getContentAsString()).contains("complete","검증된 수정본");
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        verify(dialogue,times(1)).chat(3L,"hello");
    }
    @Test void failedProcessingDoesNotSendCompleteOrExposeProviderSecrets() throws Exception {
        owned(1L);
        when(dialogue.chat(3L,"hello")).thenAnswer(call->{
            DialoguePreview.generated("미확정 본문");
            throw new IllegalStateException("SECRET provider response");
        });
        var response=new MockHttpServletResponse();
        controller.chat(1L,2L,3L,new WorldRoomChatRequest("hello"),auth,response);
        assertThat(response.getContentAsString()).contains("preview","failed").doesNotContain("complete","SECRET");
        verify(dialogue,times(1)).chat(3L,"hello");
    }
    @Test void checksOwnershipAndWorldBeforeStartingOrGenerating() {
        owned(99L);
        var response=new MockHttpServletResponse();
        assertThatThrownBy(()->controller.chat(1L,2L,3L,new WorldRoomChatRequest("hello"),auth,response))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(response.isCommitted()).isFalse();
        verifyNoInteractions(dialogue);
    }
}
