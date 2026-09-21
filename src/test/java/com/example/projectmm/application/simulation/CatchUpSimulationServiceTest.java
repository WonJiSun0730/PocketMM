package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.simulation.CatchUpResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CatchUpSimulationServiceTest {

    private final CatchUpSimulationService service =
            new CatchUpSimulationService();

    @Test
    void 짧은_미접속은_전체_시간을_진행한다() {
        LocalDateTime last =
                LocalDateTime.of(2026, 9, 21, 10, 0);

        LocalDateTime now =
                LocalDateTime.of(2026, 9, 21, 13, 0);

        CatchUpResult result =
                service.calculate(last, now);

        assertThat(result.simulatedElapsed().toHours())
                .isEqualTo(3);

        assertThat(result.capped())
                .isFalse();
    }

    @Test
    void 긴_미접속은_ProgressCap을_적용한다() {
        LocalDateTime last =
                LocalDateTime.of(2026, 8, 1, 10, 0);

        LocalDateTime now =
                LocalDateTime.of(2026, 9, 21, 10, 0);

        CatchUpResult result =
                service.calculate(last, now);

        assertThat(result.simulatedElapsed().toDays())
                .isEqualTo(3);

        assertThat(result.capped())
                .isTrue();
    }
}