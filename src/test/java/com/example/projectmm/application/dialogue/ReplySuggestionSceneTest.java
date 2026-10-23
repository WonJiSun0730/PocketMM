package com.example.projectmm.application.dialogue;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.domain.chat.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReplySuggestionSceneTest {
    final WorldRoomSessionService sessions=mock(WorldRoomSessionService.class);
    final WorldRoomMessageRepository messages=mock(WorldRoomMessageRepository.class);
    final WorldRoomPlayerStateRepository players=mock(WorldRoomPlayerStateRepository.class);
    final ReplySuggestionScene scenes=new ReplySuggestionScene(sessions,messages,players);
    WorldRoomSession owned() {
        var session=mock(WorldRoomSession.class,RETURNS_DEEP_STUBS);
        when(sessions.ownedSession(2L,3L,"owner")).thenReturn(session);
        when(session.getRoom().getWorld().getId()).thenReturn(1L);
        when(session.getPlayerProfile()).thenReturn(null);
        when(session.getIntroContentSnapshot()).thenReturn("intro");
        when(players.findBySessionId(3L)).thenReturn(Optional.empty());return session;
    }
    WorldRoomMessage latest() {
        var last=mock(WorldRoomMessage.class);
        when(last.getId()).thenReturn(9L);when(last.getSelectedVariantNo()).thenReturn(1);
        when(last.getRole()).thenReturn(ChatRole.CHARACTER);when(last.getContent()).thenReturn("current passage");
        when(messages.findTop15BySessionIdOrderByIdDesc(3L)).thenReturn(List.of(last));return last;
    }
    @Test void validatesOwnershipAndWorldBeforeReadingMessages() {
        owned();
        assertThatThrownBy(()->scenes.prepare(99L,2L,3L,"owner",new ReplySuggestionScene.Source(null,null,null)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(messages,players);
    }
    @Test void staleVariantOrEditedContentCannotTriggerRecommendation() {
        owned();latest();
        assertThatThrownBy(()->scenes.prepare(1L,2L,3L,"owner",new ReplySuggestionScene.Source(9L,2,"current passage")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->scenes.prepare(1L,2L,3L,"owner",new ReplySuggestionScene.Source(9L,1,"old text")))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(players);
    }
    @Test void keyChangesWithPassageVariantAndTextButNotToggle() {
        owned();var last=latest();var source=new ReplySuggestionScene.Source(9L,1,"current passage");
        var first=scenes.prepare(1L,2L,3L,"owner",source);
        assertThat(scenes.prepare(1L,2L,3L,"owner",source).key()).isEqualTo(first.key());
        when(last.getSelectedVariantNo()).thenReturn(2);
        assertThat(scenes.prepare(1L,2L,3L,"owner",new ReplySuggestionScene.Source(9L,2,"current passage")).key()).isNotEqualTo(first.key());
        when(last.getContent()).thenReturn("edited");
        assertThat(scenes.prepare(1L,2L,3L,"owner",new ReplySuggestionScene.Source(9L,2,"edited")).evidence()).contains("edited");
        verify(messages,never()).save(any());verify(players,never()).save(any());
    }
    @Test void openingIntroCanBeSuggestedWithoutCreatingDialogue() {
        owned();when(messages.findTop15BySessionIdOrderByIdDesc(3L)).thenReturn(List.of());
        assertThat(scenes.prepare(1L,2L,3L,"owner",new ReplySuggestionScene.Source(null,null,null)).evidence()).contains("intro");
        verify(messages,never()).save(any());
    }
    @Test void currentPlayerPossessionsAreIncludedWithoutMemoryOrStateMutation() {
        owned();latest();
        var state=mock(WorldRoomPlayerState.class);
        when(state.getLocation()).thenReturn("carriage");
        when(state.getInventory()).thenReturn(List.of("medical bag"));
        when(state.getConditions()).thenReturn(List.of());
        when(players.findBySessionId(3L)).thenReturn(Optional.of(state));
        assertThat(scenes.prepare(1L,2L,3L,"owner",new ReplySuggestionScene.Source(9L,1,"current passage")).evidence())
                .contains("medical bag","carriage");
        verify(players,never()).save(any());verify(messages,never()).save(any());
    }
}
