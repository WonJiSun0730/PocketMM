package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.simulation.CatchUpSegment;
import com.example.projectmm.domain.simulation.SegmentPeriod;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CatchUpSegmentService {

    public List<CatchUpSegment> createSegments(
            LocalDateTime from,
            LocalDateTime to
    ) {
        List<CatchUpSegment> segments = new ArrayList<>();

        LocalDateTime cursor = from;

        while (cursor.isBefore(to)) {

            LocalDateTime boundary = nextBoundary(cursor);

            LocalDateTime segmentEnd =
                    boundary.isBefore(to) ? boundary : to;

            segments.add(
                    new CatchUpSegment(
                            cursor,
                            segmentEnd,
                            determinePeriod(cursor)
                    )
            );

            cursor = segmentEnd;
        }

        return segments;
    }

    private LocalDateTime nextBoundary(LocalDateTime time) {

        int hour = time.getHour();

        if (hour < 6) {
            return time.toLocalDate().atTime(6, 0);
        }

        if (hour < 12) {
            return time.toLocalDate().atTime(12, 0);
        }

        if (hour < 18) {
            return time.toLocalDate().atTime(18, 0);
        }

        if (hour < 22) {
            return time.toLocalDate().atTime(22, 0);
        }

        return time.toLocalDate()
                .plusDays(1)
                .atTime(6, 0);
    }

    private SegmentPeriod determinePeriod(LocalDateTime time) {

        int hour = time.getHour();

        if (hour < 6) {
            return SegmentPeriod.NIGHT;
        }

        if (hour < 12) {
            return SegmentPeriod.MORNING;
        }

        if (hour < 18) {
            return SegmentPeriod.DAYTIME;
        }

        if (hour < 22) {
            return SegmentPeriod.EVENING;
        }

        return SegmentPeriod.NIGHT;
    }
}