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

    @Test
    void 같은_시각이면_진행하지_않는다() {
        LocalDateTime time =
                LocalDateTime.of(2026, 9, 30, 12, 0);

        var result =
                service.calculate(time, time);

        assertThat(result.actualElapsed())
                .isEqualTo(java.time.Duration.ZERO);

        assertThat(result.simulatedElapsed())
                .isEqualTo(java.time.Duration.ZERO);

        assertThat(result.to())
                .isEqualTo(time);

        assertThat(result.capped())
                .isFalse();
    }

    @Test
    void 현재시각이_마지막진행시각보다_과거면_진행하지_않는다() {
        LocalDateTime last =
                LocalDateTime.of(2026, 9, 30, 12, 0);

        LocalDateTime now =
                last.minusSeconds(1);

        var result =
                service.calculate(last, now);

        assertThat(result.actualElapsed())
                .isEqualTo(java.time.Duration.ZERO);

        assertThat(result.simulatedElapsed())
                .isEqualTo(java.time.Duration.ZERO);

        assertThat(result.to())
                .isEqualTo(last);

        assertThat(result.capped())
                .isFalse();
    }

    @Test
    void 정확히_3일이면_ProgressCap을_적용한것으로_표시하지_않는다() {
        LocalDateTime last =
                LocalDateTime.of(2026, 9, 27, 12, 0);

        LocalDateTime now =
                last.plusDays(3);

        var result =
                service.calculate(last, now);

        assertThat(result.actualElapsed())
                .isEqualTo(java.time.Duration.ofDays(3));

        assertThat(result.simulatedElapsed())
                .isEqualTo(java.time.Duration.ofDays(3));

        assertThat(result.to())
                .isEqualTo(now);

        assertThat(result.capped())
                .isFalse();
    }

    @Test
    void ProgressCap을_1초_초과하면_cap을_적용한다() {
        LocalDateTime last =
                LocalDateTime.of(2026, 9, 27, 12, 0);

        LocalDateTime now =
                last.plusDays(3).plusSeconds(1);

        var result =
                service.calculate(last, now);

        assertThat(result.actualElapsed())
                .isEqualTo(java.time.Duration.ofDays(3).plusSeconds(1));

        assertThat(result.simulatedElapsed())
                .isEqualTo(java.time.Duration.ofDays(3));

        assertThat(result.to())
                .isEqualTo(last.plusDays(3));

        assertThat(result.capped())
                .isTrue();
    }
}