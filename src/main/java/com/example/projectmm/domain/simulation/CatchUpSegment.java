package com.example.projectmm.domain.simulation;

import java.time.LocalDateTime;

public record CatchUpSegment(
        LocalDateTime start,
        LocalDateTime end,
        SegmentPeriod period
) {
}