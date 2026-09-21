package com.example.projectmm.domain.simulation;

import java.util.List;

public record CatchUpExecutionResult(
        CatchUpResult catchUp,
        List<CatchUpSegment> segments,
        List<EventCandidate> candidates,
        List<ResolvedEvent> resolvedEvents
) {
}