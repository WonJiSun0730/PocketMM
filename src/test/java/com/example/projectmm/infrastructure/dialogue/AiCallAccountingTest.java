package com.example.projectmm.infrastructure.dialogue;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiCallAccountingTest {
    @Test void auxiliaryCallsDoNotOverwritePrimaryUsageAndScopesRestoreAfterFailure() {
        var tracker=new AiUsageTracker();
        tracker.record(1L,"OPENAI","gpt-5.6-sol",100,0,100L,10,110);
        AiUsageTracker.withScope(1L,"MEMORY_EXTRACTION",()->{
            tracker.record(AiUsageTracker.currentSessionId(),"OPENAI","gpt-5.6-luna",50,0,50L,5,55);
            return null;
        });
        assertThat(tracker.latest(1L).model()).isEqualTo("gpt-5.6-sol");
        assertThat(tracker.calls(1L)).extracting(AiUsageTracker.Call::purpose).containsExactly("ROLEPLAY","MEMORY_EXTRACTION");
        assertThatThrownBy(()->AiUsageTracker.withScope(1L,"SCENE_SETUP",()->{throw new IllegalStateException();}));
        assertThat(AiUsageTracker.currentSessionId()).isNull();
    }
    @Test void unknownOrFailedUsageIsVisibleInsteadOfBeingCountedAsFree() {
        var tracker=new AiUsageTracker();
        assertThatThrownBy(()->tracker.accountRequest(1L,"OPENAI","model",()->{throw new IllegalStateException();}));
        assertThat(tracker.calls(1L)).hasSize(1);
        assertThat(tracker.calls(1L).getFirst().usage()).isNull();
        assertThat(tracker.calls(1L).getFirst().status()).isEqualTo("REQUEST_FAILED");
        assertThat(tracker.latest(1L)).isNull();
    }
    @Test void promptDiagnosticsStoreOnlyHashesAndDetectModelOrPrefixChanges() {
        var tracker=new AiUsageTracker();
        tracker.recordPromptDiagnostics(1L,"model","fixed-secret-content","dynamic-one");
        tracker.recordPromptDiagnostics(1L,"model","fixed-secret-content","dynamic-two");
        assertThat(tracker.promptDiagnostics(1L).sameStablePrefixAsPrevious()).isTrue();
        assertThat(tracker.promptDiagnostics(1L).toString()).doesNotContain("fixed-secret-content","dynamic-two");
        tracker.recordPromptDiagnostics(1L,"other-model","fixed-secret-content","dynamic-two");
        assertThat(tracker.promptDiagnostics(1L).sameStablePrefixAsPrevious()).isFalse();
    }
}
