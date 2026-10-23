package com.example.projectmm.application.dialogue;

import java.util.*;
import java.util.function.Supplier;

/** Request-scoped advisory findings shared by production and QA. */
public final class SceneContinuityObservation {
    private SceneContinuityObservation() {}
    public record Finding(String mode, String type, String message, SceneContinuityService.Review review) {}
    private static final ThreadLocal<List<Finding>> findings = new ThreadLocal<>();
    public static boolean enabled() {return findings.get()!=null;}
    public static List<Finding> current() {return enabled()?List.copyOf(findings.get()):List.of();}
    static void record(String mode,String type,String message,SceneContinuityService.Review review) {
        if(!enabled())throw new IllegalStateException("Continuity observation scope required");
        findings.get().add(new Finding(mode,type,message,review));
    }
    public static <T> T observe(Supplier<T> action) {
        if(enabled())throw new IllegalStateException("Nested continuity observation is not supported");
        findings.set(new ArrayList<>());
        try {return action.get();} finally {findings.remove();}
    }
}
