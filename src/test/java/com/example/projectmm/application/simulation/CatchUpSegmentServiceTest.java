package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.simulation.CatchUpSegment;
import com.example.projectmm.domain.simulation.SegmentPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatchUpSegmentServiceTest {

    private final CatchUpSegmentService service =
            new CatchUpSegmentService();

    @Test
    void 시간을_시간대별_Segment로_분할한다() {

        LocalDateTime from =
                LocalDateTime.of(2026, 9, 20, 15, 30);

        LocalDateTime to =
                LocalDateTime.of(2026, 9, 21, 10, 0);

        List<CatchUpSegment> segments =
                service.createSegments(from, to);

        assertThat(segments).hasSize(4);

        assertThat(segments)
                .extracting(CatchUpSegment::period)
                .containsExactly(
                        SegmentPeriod.DAYTIME,
                        SegmentPeriod.EVENING,
                        SegmentPeriod.NIGHT,
                        SegmentPeriod.MORNING
                );
    }
}