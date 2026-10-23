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
    @Test void rejectedReviewKeepsOriginalAndStillAppliesSupportedUserFact() {
        var original=turn("The empty cup rests on the table.");
        var fact=new SceneFacts.Change("tea.status",SceneFacts.Kind.COMPLETION,"finished",
                SceneFacts.Certainty.OBSERVED,SceneFacts.Source.USER,"I finished the tea",null,SceneFacts.Reason.NEW);
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(original);
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenReturn(json.writeValueAsString(
                new SceneContinuityService.Review(false,turn("rewritten"),List.of(fact),List.of("cup conflict"))));
        SceneContinuityObservation.observe(()->{
            var outcome=service.generate(context(),"I finished the tea","I finished the tea",100L);
            assertThat(outcome.turn()).isEqualTo(original);
            assertThat(outcome.after()).contains("finished");
            assertThat(SceneContinuityObservation.current()).hasSize(1);
            return null;
        });
        verify(gateway,times(1)).reviewWorldRoomContinuity(any(),anyString());
    }
    @Test void malformedReviewNeverCancelsGeneratedBodyAndPreviewScopeClears() {
        var previews=new ArrayList<String>();
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(turn("original"));
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenReturn("{}","not json",null);
        for(int i=0;i<3;i++)assertThat(DialoguePreview.deliver(previews::add,
                ()->service.generate(context(),"continue","",100L)).turn().reply()).isEqualTo("original");
        assertThat(previews).containsExactly("original","original","original");
        service.generate(context(),"continue","",100L);
        assertThat(previews).hasSize(3);
    }
    @Test void invalidFactDoesNotDiscardOtherSupportedFactsOrRewriteOriginal() {
        var good=new SceneFacts.Change("tea.status",SceneFacts.Kind.COMPLETION,"finished",
                SceneFacts.Certainty.OBSERVED,SceneFacts.Source.USER,"finished",null,SceneFacts.Reason.NEW);
        var bad=new SceneFacts.Change("bag.status",SceneFacts.Kind.POSSESSION,"carried",
                SceneFacts.Certainty.OBSERVED,SceneFacts.Source.REPLY,"invented quote",null,SceneFacts.Reason.NEW);
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(turn("original"));
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenReturn(result(turn("rewrite"),List.of(bad,good)));
        var outcome=service.generate(context(),"finished","finished",100L);
        assertThat(outcome.turn().reply()).isEqualTo("original");
        assertThat(outcome.after()).contains("tea.status").doesNotContain("bag.status");
        verify(gateway,times(1)).reviewWorldRoomContinuity(any(),anyString());
    }
    @Test void nonStreamingProductionAlsoPersistsAdvisoryAndKeepsOriginal() throws Exception {
        var store=mock(ChatFailureStore.class);
        var production=new SceneContinuityService(gateway,messages,variants,store);
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(turn("original"));
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenReturn(json.writeValueAsString(
                new SceneContinuityService.Review(false,null,List.of(),List.of("ambiguous"))));
        assertThat(production.generate(context(),"continue","",100L).turn().reply()).isEqualTo("original");
        verify(store).save(argThat(e->e.phase().equals("ADVISORY") && e.conflicts().contains("ambiguous")));
        assertThat(SceneContinuityObservation.enabled()).isFalse();
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
