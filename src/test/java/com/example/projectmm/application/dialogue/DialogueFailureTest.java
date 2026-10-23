package com.example.projectmm.application.dialogue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

class DialogueFailureTest {
    @Test void separatesPreparationGenerationReviewAndPostReviewWithoutClaimingRollback() {
        var timing=new DialogueTiming(()->0);
        var failure=new IllegalStateException("SECRET provider body and key");
        assertThat(DialogueFailure.describe(failure,timing.snapshot()).phase()).isEqualTo("PREPARATION");
        timing.measure(()->{
            DialogueTiming.generationStarting();
            assertThat(DialogueFailure.describe(failure,timing.snapshot()).phase()).isEqualTo("GENERATION");
            DialogueTiming.bodyReady();
            assertThat(DialogueFailure.describe(failure,timing.snapshot()).phase()).isEqualTo("CONTINUITY");
            DialogueTiming.reviewed();return null;
        });
        var detail=DialogueFailure.describe(failure,timing.snapshot());
        assertThat(detail.phase()).isEqualTo("PERSISTENCE_OR_MEMORY");
        assertThat(new ObjectMapper().writeValueAsString(detail)+DialogueFailure.message(detail,"id"))
                .doesNotContain("SECRET","provider body","rollback");
        assertThat(new DialogueTiming("POCKET_SERVER").snapshot().source()).isEqualTo("POCKET_SERVER");
        assertThat(new DialogueTiming().snapshot().source()).isEqualTo("QA_SERVER");
    }
    @Test void wrappedRejectionHasAStableCodeWithoutExposingModelConflicts() {
        var review=new SceneContinuityService.Review(false,null,java.util.List.of(),java.util.List.of("SECRET narrative"));
        var failure=new RuntimeException("SECRET outer",new SceneContinuityService.ReviewRejectedException(review));
        var detail=DialogueFailure.describe(failure,new DialogueTiming().snapshot());
        assertThat(detail.code()).isEqualTo("CONTINUITY_REJECTED");
        assertThat(DialogueFailure.message(detail,"test-id")).contains("test-id","CONTINUITY_REJECTED").doesNotContain("SECRET");
        assertThat(detail.exceptionTypes()).hasSize(2);
    }
    @Test void exceptionCauseCyclesAreBounded() {
        var a=new RuntimeException("SECRET");var b=new RuntimeException("SECRET");a.initCause(b);b.initCause(a);
        assertThat(DialogueFailure.describe(a,new DialogueTiming().snapshot()).exceptionTypes()).hasSize(2);
    }
}
