package com.example.projectmm.domain.dialogue;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.example.projectmm.domain.dialogue.SceneFacts.*;

class SceneFactsTest {
    @Test void repeatedMentionPreservesOriginalEvidenceAndEmptyDeltaPreservesEveryFact() {
        var fact = observed("letter.read", Kind.COMPLETION, "read");
        var before = List.of(fact);
        var repeat = new Change(fact.key(), fact.kind(), fact.value(), fact.certainty(),
                Source.USER, "read", "read", Reason.EVENT);
        assertThat(SceneFacts.apply(before, List.of(repeat), "already read", "", "")).containsExactly(fact);
        assertThat(SceneFacts.apply(before, List.of(), "", "", "")).containsExactly(fact);
        var forged = new Change(fact.key(), fact.kind(), fact.value(), fact.certainty(),
                Source.USER, "invented", "read", Reason.EVENT);
        assertThatThrownBy(() -> SceneFacts.apply(before, List.of(forged), "read", "", ""))
                .hasMessageContaining("evidence");
    }
    private Fact observed(String key, Kind kind, String value) {
        return new Fact(key, kind, value, Certainty.OBSERVED, Source.REPLY, "established");
    }
    @Test void reportedSeventhPositionDoesNotReplaceObservedDigit() {
        var before = List.of(observed("patient.response.digit", Kind.UNCERTAIN, "7"));
        var claim = new Change("player.report.position", Kind.UNCERTAIN, "seventh position",
                Certainty.CLAIM, Source.USER, "seventh position", null, Reason.NEW);
        var after = SceneFacts.apply(before, List.of(claim), "I meant the seventh position.", "", "");
        assertThat(after).extracting(Fact::value).containsExactly("7", "seventh position");
        var overwrite = new Change("patient.response.digit", Kind.UNCERTAIN, "seventh position",
                Certainty.CLAIM, Source.USER, "seventh position", "7", Reason.EVENT);
        assertThatThrownBy(() -> SceneFacts.apply(before,List.of(overwrite),"seventh position","",""))
                .hasMessageContaining("own key");
    }
    @Test void receiptCustodyCannotChangeWithoutSourceEvidenceOrWithStalePredecessor() {
        var before = List.of(observed("police.receipt.custodian", Kind.POSSESSION, "Eleanor"));
        var invented = new Change("police.receipt.custodian", Kind.POSSESSION, "nurse",
                Certainty.OBSERVED, Source.REPLY, "hands the receipt", "Eleanor", Reason.EVENT);
        assertThatThrownBy(() -> SceneFacts.apply(before,List.of(invented),"","The nurse enters.",""))
                .hasMessageContaining("evidence");
        var stale = new Change("police.receipt.custodian", Kind.POSSESSION, "nurse",
                Certainty.OBSERVED, Source.REPLY, "hands the receipt", "Holmes", Reason.EVENT);
        assertThatThrownBy(() -> SceneFacts.apply(before,List.of(stale),"","hands the receipt",""))
                .hasMessageContaining("predecessor");
        assertThat(before.getFirst().value()).isEqualTo("Eleanor");
    }
    @Test void explicitUserRevisionIsAllowedButAssistantCannotAuthorizeIt() {
        var before = List.of(observed("patient.identity", Kind.PERSON, "woman"));
        var direction = new Change("patient.identity", Kind.PERSON, "man", Certainty.OBSERVED,
                Source.USER, "make the patient a man", "woman", Reason.USER_DIRECTION);
        assertThat(SceneFacts.apply(before,List.of(direction),"OOC: make the patient a man","",""))
                .extracting(Fact::value).containsExactly("man");
        var forged = new Change("patient.identity", Kind.PERSON, "man", Certainty.OBSERVED,
                Source.REPLY, "man", "woman", Reason.USER_DIRECTION);
        assertThatThrownBy(() -> SceneFacts.apply(before,List.of(forged),"","man",""))
                .hasMessageContaining("user");
    }
    @Test void foldedStorageKeepsReadCompletionAndIdentifierNamespacesSeparate() {
        var before = List.of(observed("letter.read", Kind.COMPLETION, "read"),
                observed("police.case.number", Kind.IDENTIFIER, "P-17"),
                observed("hospital.receipt.number", Kind.IDENTIFIER, "H-71"));
        var stored = new Change("letter.location", Kind.POSSESSION, "notebook", Certainty.OBSERVED,
                Source.REPLY, "folded into the notebook", null, Reason.NEW);
        assertThat(SceneFacts.apply(before,List.of(stored),"","folded into the notebook",""))
                .extracting(Fact::value).containsExactly("read","P-17","H-71","notebook");
    }
}
