package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SceneContinuityServiceTest {
    final DialogueGateway gateway = mock(DialogueGateway.class);
    final WorldRoomMessageRepository messages = mock(WorldRoomMessageRepository.class);
    final WorldRoomMessageVariantRepository variants = mock(WorldRoomMessageVariantRepository.class);
    final SceneContinuityService service = new SceneContinuityService(gateway,messages,variants);
    final ObjectMapper json = new ObjectMapper();
    WorldRoomDialogueContext context() {
        return new WorldRoomDialogueContext(1L,2L,"scene",WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(1889,1,1,12,0),null,null,"intro","",List.of(),List.of(),List.of(),
                List.of(),WorldRoomPlayerStateContext.empty(),List.of(),null,null,false,"OPENAI","model");
    }
    WorldRoomTurn turn(String text) { return new WorldRoomTurn(text,WorldRoomCastChange.none()); }
    String result(WorldRoomTurn turn,List<SceneFacts.Change> changes) {
        return json.writeValueAsString(new SceneContinuityService.Review(true,turn,changes,List.of()));
    }
    @BeforeEach void emptyHistory() {
        when(variants.selectedSceneCheckpoints(anyLong(),anyLong(),any())).thenReturn(List.of());
        when(messages.sceneHistoryAfter(anyLong(),anyLong(),anyLong(),any())).thenReturn(List.of());
    }
    @Test void repairsContradictionThenVerifiesAndUsesOnlyApprovedEvidence() {
        var wrong=turn("The receipt is in Eleanor's notebook. The nurse takes it from Holmes.");
        var fixed=turn("The receipt remains in Eleanor's notebook.");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(wrong);
        var fact=new SceneFacts.Change("receipt.custodian",SceneFacts.Kind.POSSESSION,"Eleanor",
                SceneFacts.Certainty.OBSERVED,SceneFacts.Source.REPLY,"Eleanor's notebook",null,SceneFacts.Reason.NEW);
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenAnswer(call -> {
            var request=json.readTree((String)call.getArgument(1));
            return result(request.path("mode").asText().equals("REVIEW")?fixed:null,List.of(fact));
        });
        var outcome=service.generate(context(),"continue","",Long.MAX_VALUE);
        assertThat(outcome.turn()).isEqualTo(fixed);
        assertThat(outcome.after()).contains("Eleanor").doesNotContain("Holmes","nurse");
        verify(gateway,times(2)).reviewWorldRoomContinuity(any(),anyString());
        verifyNoInteractionsExceptReads();
    }
    @Test void previewArrivesBeforeReviewButDoesNotApproveOrReplaceFinalTurn() {
        var order = new ArrayList<String>();
        var candidate = turn("provisional");
        var repaired = turn("approved repair");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(candidate);
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenAnswer(call -> {
            assertThat(order).contains("preview:provisional");
            order.add("review");
            var mode=json.readTree((String)call.getArgument(1)).path("mode").asText();
            return result(mode.equals("REVIEW") ? repaired : null,List.of());
        });
        var outcome=DialoguePreview.deliver(reply -> order.add("preview:"+reply),
                () -> service.generate(context(),"continue","",Long.MAX_VALUE));
        assertThat(outcome.turn()).isEqualTo(repaired);
        assertThat(order).containsExactly("preview:provisional","review","review");
    }
    @Test void rejectedPreviewCannotEscapeAsApprovedAndScopeIsAlwaysCleared() {
        var previews=new ArrayList<String>();
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(turn("untrusted"));
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenReturn("{}");
        assertThatThrownBy(() -> DialoguePreview.deliver(previews::add,
                () -> service.generate(context(),"continue","",Long.MAX_VALUE)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(previews).containsExactly("untrusted");
        assertThatThrownBy(() -> service.generate(context(),"continue","",Long.MAX_VALUE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(previews).containsExactly("untrusted");
        verifyNoInteractionsExceptReads();
    }
    private void verifyNoInteractionsExceptReads() {
        verify(messages,never()).save(any());
        verify(variants,never()).save(any());
    }
    @Test void failedVerificationNeverReturnsOrCommitsAResponse() {
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(turn("wrong identity"));
        when(gateway.reviewWorldRoomContinuity(any(),anyString()))
                .thenReturn(result(turn("still wrong"),List.of()),
                        json.writeValueAsString(new SceneContinuityService.Review(false,null,List.of(),List.of("identity mismatch"))));
        assertThatThrownBy(() -> service.generate(context(),"continue","",Long.MAX_VALUE))
                .hasMessageContaining("did not approve");
        verifyNoInteractionsExceptReads();
    }
    @Test void malformedAndUnavailableReviewFailClosed() {
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(turn("reply"));
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenReturn("{}","not json",null);
        for(int i=0;i<3;i++)assertThatThrownBy(() -> service.generate(context(),"continue","",Long.MAX_VALUE))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractionsExceptReads();
    }
    @Test void factOutsideRecentWindowStillReachesEveryGenerationAndAiSelectionCopy() {
        var checkpoint=mock(WorldRoomMessageVariant.class);
        var message=mock(WorldRoomMessage.class);
        when(message.getId()).thenReturn(10L);
        when(checkpoint.getMessage()).thenReturn(message);
        var fact=new SceneFacts.Fact("police.case.number",SceneFacts.Kind.IDENTIFIER,"P-17",
                SceneFacts.Certainty.OBSERVED,SceneFacts.Source.REPLY,"P-17");
        when(checkpoint.getAfterSceneFactsJson()).thenReturn(json.writeValueAsString(List.of(fact)));
        when(variants.selectedSceneCheckpoints(anyLong(),anyLong(),any())).thenReturn(List.of(checkpoint));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(turn("continue"));
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenReturn(result(null,List.of()));
        var outcome=service.generate(context(),"continue","",100L);
        assertThat(outcome.context().sceneFactsJson()).contains("P-17");
        assertThat(outcome.context().withAiSelection("OPENAI","other").sceneFactsJson()).contains("P-17");
        verify(gateway).generateWorldRoomTurn(argThat(c->c.sceneFactsJson().contains("P-17")),eq("continue"));
        verify(messages).sceneHistoryAfter(eq(1L),eq(10L),eq(100L),any());
    }
    @Test void bootstrapReadsBeyondSixTurnWindowWithoutDroppingEarlierIdentifier() {
        var batch=new ArrayList<WorldRoomMessage>();
        for(long i=1;i<=12;i++){
            var m=mock(WorldRoomMessage.class);when(m.getId()).thenReturn(i);when(m.getRole()).thenReturn(ChatRole.CHARACTER);
            when(m.getContent()).thenReturn(i==1?"Hospital number H-71":"quiet scene "+i);batch.add(m);
        }
        var thirteenth=mock(WorldRoomMessage.class);when(thirteenth.getId()).thenReturn(13L);
        when(thirteenth.getRole()).thenReturn(ChatRole.CHARACTER);when(thirteenth.getContent()).thenReturn("Later scene");
        when(messages.sceneHistoryAfter(anyLong(),anyLong(),anyLong(),any()))
                .thenReturn(batch,List.of(thirteenth),List.of());
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(turn("continue"));
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenAnswer(call -> {
            var request=json.readTree((String)call.getArgument(1));
            if(request.path("history").toString().contains("H-71"))
                return result(null,List.of(new SceneFacts.Change("hospital.receipt.number",SceneFacts.Kind.IDENTIFIER,"H-71",
                        SceneFacts.Certainty.OBSERVED,SceneFacts.Source.HISTORY,"Hospital number H-71",null,SceneFacts.Reason.NEW)));
            return result(null,List.of());
        });
        var outcome=service.generate(context(),"continue","",100L);
        assertThat(outcome.before()).contains("H-71");
        verify(gateway).generateWorldRoomTurn(argThat(c->c.sceneFactsJson().contains("H-71")),anyString());
        verify(messages).sceneHistoryAfter(eq(1L),eq(12L),eq(100L),any());
    }
}
