package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.chat.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PocketHistoryWindowTest {
    private WorldRoomMessage message(ChatRole role) {
        var message = mock(WorldRoomMessage.class);
        when(message.getRole()).thenReturn(role);
        return message;
    }
    @Test void keepsSixCompleteTurnsAndPendingRequest() {
        var history = new ArrayList<WorldRoomMessage>();
        for (int i=0;i<9;i++) {history.add(message(ChatRole.USER));history.add(message(ChatRole.CHARACTER));}
        history.add(message(ChatRole.USER));
        assertThat(PocketHistoryWindow.recent(history,6)).containsExactlyElementsOf(history.subList(6,19));
        assertThat(history).hasSize(19);
    }
    @Test void automaticResponseCountsAsItsOwnCompletedTurn() {
        var history = List.of(message(ChatRole.USER),message(ChatRole.CHARACTER),
                message(ChatRole.USER),message(ChatRole.CHARACTER),message(ChatRole.CHARACTER));
        assertThat(PocketHistoryWindow.recent(history,1)).containsExactly(history.getLast());
        assertThat(PocketHistoryWindow.recent(history,2)).containsExactlyElementsOf(history.subList(2,5));
    }
    @Test void thirtyAutomaticResponsesKeepOnlyLastSixWithPendingInputSeparate() {
        var history = new ArrayList<WorldRoomMessage>();
        history.add(message(ChatRole.USER));
        for(int i=0;i<30;i++) history.add(message(ChatRole.CHARACTER));
        assertThat(PocketHistoryWindow.recent(history,6)).containsExactlyElementsOf(history.subList(25,31));
        history.add(message(ChatRole.USER));
        assertThat(PocketHistoryWindow.recent(history,6)).containsExactlyElementsOf(history.subList(25,32));
    }
    @Test void automaticOnlyAndMixedHistoriesHaveSameSixResponseLimit() {
        var history = new ArrayList<WorldRoomMessage>();
        for(int i=0;i<30;i++) history.add(message(ChatRole.CHARACTER));
        assertThat(PocketHistoryWindow.recent(history,6)).containsExactlyElementsOf(history.subList(24,30));
        history.clear();
        for(int i=0;i<10;i++) {
            if(i%3==0)history.add(message(ChatRole.USER));
            history.add(message(ChatRole.CHARACTER));
        }
        assertThat(PocketHistoryWindow.recent(history,6).stream().filter(m->m.getRole()==ChatRole.CHARACTER)).hasSize(6);
    }
    @Test void shortAndEmptyHistoriesAreUnchanged() {
        assertThat(PocketHistoryWindow.recent(List.of(),6)).isEmpty();
        var history=List.of(message(ChatRole.USER));
        assertThat(PocketHistoryWindow.recent(history,6)).containsExactlyElementsOf(history);
    }
}
