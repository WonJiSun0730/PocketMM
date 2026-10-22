package com.example.projectmm.application.dialogue;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SceneContinuityObservationTest {
    @Test void qaRecordsRejectedOrMalformedReviewsAndKeepsOriginalWhileProductionStillBlocks() {
        var f=new SceneContinuityServiceTest();f.emptyHistory();
        var candidate=f.turn("original target output");
        when(f.gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(candidate);
        for(String response:List.of("not json","{}","",f.json.writeValueAsString(
                new SceneContinuityService.Review(false,f.turn("reviewer rewrite"),List.of(),List.of("conflict"))))) {
            when(f.gateway.reviewWorldRoomContinuity(any(),anyString())).thenReturn(response);
            SceneContinuityObservation.observe(()->{
                var result=f.service.generate(f.context(),"continue","",Long.MAX_VALUE);
                assertThat(result.turn()).isEqualTo(candidate);
                assertThat(result.after()).isEqualTo(result.before());
                assertThat(SceneContinuityObservation.current()).hasSize(1);
                return null;
            });
            assertThat(SceneContinuityObservation.enabled()).isFalse();
            assertThatThrownBy(()->f.service.generate(f.context(),"continue","",Long.MAX_VALUE))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void bootstrapFormatErrorIsRecordedAndDoesNotPreventCurrentTurn() {
        var f=new SceneContinuityServiceTest();f.emptyHistory();
        var history=mock(com.example.projectmm.domain.chat.WorldRoomMessage.class);
        when(history.getId()).thenReturn(1L);
        when(history.getRole()).thenReturn(com.example.projectmm.domain.chat.ChatRole.CHARACTER);
        when(history.getContent()).thenReturn("committed history");
        when(f.messages.sceneHistoryAfter(anyLong(),anyLong(),anyLong(),any())).thenReturn(List.of(history),List.of());
        when(f.gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(f.turn("target original"));
        when(f.gateway.reviewWorldRoomContinuity(any(),anyString())).thenAnswer(call->
                f.json.readTree((String)call.getArgument(1)).path("mode").asText().equals("BOOTSTRAP")
                        ?"invalid history review":f.result(null,List.of()));
        SceneContinuityObservation.observe(()->{
            assertThat(f.service.generate(f.context(),"continue","",Long.MAX_VALUE).turn().reply()).isEqualTo("target original");
            assertThat(SceneContinuityObservation.current()).singleElement().satisfies(issue->{
                assertThat(issue.mode()).isEqualTo("BOOTSTRAP");assertThat(issue.type()).isEqualTo("INVALID_JSON");
            });
            return null;
        });
        verify(f.gateway,times(2)).reviewWorldRoomContinuity(any(),anyString());
    }
    @Test void networkOrBudgetFailuresNeverBecomeContinuityObservationsAndScopeAlwaysClears() {
        var f=new SceneContinuityServiceTest();f.emptyHistory();
        when(f.gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(f.turn("target"));
        for(RuntimeException failure:List.of(new ResourceAccessException("transport unavailable"),
                new IllegalStateException("Budget policy already blocked this run"))) {
            doThrow(failure).when(f.gateway).reviewWorldRoomContinuity(any(),anyString());
            assertThatThrownBy(()->SceneContinuityObservation.observe(()->
                    f.service.generate(f.context(),"continue","",Long.MAX_VALUE))).isSameAs(failure);
            assertThat(SceneContinuityObservation.enabled()).isFalse();
        }
    }
}
