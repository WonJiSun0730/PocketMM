package com.example.projectmm.domain.state;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CustomStateChangeProposalTest {
    @Test void calculatesDeltaWithoutClampingOrUsingAnAiBaseValue() {
        var proposal = new CustomStateChangeProposal(1L, -5.0);
        assertThat(proposal.resolve(80, 0, 100)).isEqualTo(75);
        assertThat(proposal.resolve(67, 0, 100)).isEqualTo(62);
        assertThatIllegalArgumentException().isThrownBy(() -> proposal.resolve(0, 0, 100));
    }

    @Test void rejectsInvalidStructureAndNonFiniteInputsAndOverflow() {
        for (var proposal : java.util.List.of(new CustomStateChangeProposal(null, 0.0),
                new CustomStateChangeProposal(0L, 0.0), new CustomStateChangeProposal(1L, null),
                new CustomStateChangeProposal(1L, Double.NaN), new CustomStateChangeProposal(1L, Double.POSITIVE_INFINITY),
                new CustomStateChangeProposal(1L, Double.NEGATIVE_INFINITY))) {
            assertThatIllegalArgumentException().isThrownBy(proposal::validate);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomStateChangeProposal(1L, Double.MAX_VALUE)
                .resolve(Double.MAX_VALUE, 0, Double.MAX_VALUE));
        assertThatIllegalArgumentException().isThrownBy(() -> new CustomStateChangeProposal(1L, 0.0)
                .resolve(Double.NaN, 0, 100));
    }
}
