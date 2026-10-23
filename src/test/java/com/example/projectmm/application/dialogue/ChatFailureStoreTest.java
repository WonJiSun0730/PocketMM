package com.example.projectmm.application.dialogue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ChatFailureStoreTest {
    @TempDir Path directory;
    Throwable rejected(SceneContinuityService.Review review,String mode) {
        return new RuntimeException("SECRET provider response",new SceneContinuityService.ReviewRejectedException(review,mode));
    }
    @Test void keepsConflictsAndCandidateAfterStoreReloadWithoutProviderEnvelopeOrOtherSessionRecords() throws Exception {
        String id=UUID.randomUUID().toString();
        var evidence=ChatFailureStore.evidence(rejected(new SceneContinuityService.Review(false,null,List.of(),List.of("Character was absent")),"REVIEW"),id,55L,"CONTINUITY","candidate body");
        new ChatFailureStore(directory.toString()).save(evidence);
        var stored=new ChatFailureStore(directory.toString()).latest(55L).getFirst();
        assertThat(stored.requestId()).isEqualTo(id);assertThat(stored.conflicts()).containsExactly("Character was absent");
        assertThat(stored.mode()).isEqualTo("REVIEW");assertThat(stored.candidate()).isEqualTo("candidate body");
        assertThat(stored.toString()).doesNotContain("SECRET","provider response");
        assertThat(new ChatFailureStore(directory.toString()).latest(56L)).isEmpty();
    }
    @Test void emptyConflictListStillExplainsNegativeApprovalAndMissingRequiredFields() {
        var evidence=ChatFailureStore.evidence(rejected(new SceneContinuityService.Review(false,null,null,List.of()),"BOOTSTRAP"),UUID.randomUUID().toString(),1L,"PREPARATION",null);
        assertThat(evidence.reasons()).hasSize(2);assertThat(evidence.conflicts()).isEmpty();assertThat(evidence.candidate()).isEmpty();
        assertThat(evidence.mode()).isEqualTo("BOOTSTRAP");
        assertThat(ChatFailureStore.evidence(new IllegalStateException("SECRET"),UUID.randomUUID().toString(),1L,"GENERATION",null)).isNull();
    }
    @Test void recordsAreBoundedAndMarkedWhenTruncated() throws Exception {
        var evidence=ChatFailureStore.evidence(rejected(new SceneContinuityService.Review(false,null,List.of(),Collections.nCopies(25,"x".repeat(3000))),"VERIFY"),UUID.randomUUID().toString(),1L,"CONTINUITY","y".repeat(40000));
        assertThat(evidence.truncated()).isTrue();assertThat(evidence.conflicts()).hasSize(20);
        assertThat(evidence.conflicts().getFirst()).hasSize(2000);assertThat(evidence.candidate()).hasSize(32000);
        var store=new ChatFailureStore(directory.toString());store.save(evidence);assertThat(store.latest(1L)).containsExactly(evidence);
    }
    @Test void verifyFailurePreservesTheRepairedCandidateThatWasActuallyReviewed() {
        var candidate=new com.example.projectmm.domain.dialogue.WorldRoomTurn("repaired candidate",com.example.projectmm.domain.dialogue.WorldRoomCastChange.none());
        var failure=new SceneContinuityService.ReviewRejectedException(new SceneContinuityService.Review(false,null,List.of(),List.of("repair conflict")),"VERIFY",candidate);
        var evidence=ChatFailureStore.evidence(failure,UUID.randomUUID().toString(),1L,"CONTINUITY","original preview");
        assertThat(evidence.candidate()).isEqualTo("repaired candidate");assertThat(evidence.mode()).isEqualTo("VERIFY");
    }
}
