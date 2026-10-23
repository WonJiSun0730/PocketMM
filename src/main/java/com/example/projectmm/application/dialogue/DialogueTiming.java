package com.example.projectmm.application.dialogue;

import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.*;

/** Server milestones only; never a browser receive/paint measurement. */
public final class DialogueTiming {
    private static final ThreadLocal<DialogueTiming> CURRENT = new ThreadLocal<>();
    private final LongSupplier clock;
    private final long start;
    private Long firstTokenMs, preGenerationMs, targetDispatchMs, bodyReadyMs, reviewCompleteMs, processingCompleteMs;
    public static final List<String> STAGES=List.of("SESSION_LOCK","INITIAL_SCENE_SETUP","USER_MESSAGE_SAVE",
            "PLAYER_STATE_SNAPSHOT","SCENE_INSTRUCTION","TIME_INTERPRETATION","CONTEXT_BUILD",
            "CONTEXT_MEMORY_RECALL","STATE_SNAPSHOT","SCENE_FACTS_PREPARE","TARGET_GENERATION","TARGET_TOKEN_COUNT");
    public record Stage(long durationMs,int count,int failedCount) {}
    private final Map<String,Stage> stages=new LinkedHashMap<>();
    public record Snapshot(String source, Long bodyReadyMs, Long reviewCompleteMs, Long processingCompleteMs,
                           Long preGenerationMs, Long targetDispatchMs, Map<String,Stage> stages, Long firstTokenMs) {}
    public DialogueTiming() { this(System::nanoTime); }
    public DialogueTiming(LongSupplier clock) { this.clock=clock;start=clock.getAsLong(); }
    public <T> T measure(Supplier<T> action) {
        if(CURRENT.get()!=null)throw new IllegalStateException("Nested dialogue timing");
        CURRENT.set(this);
        try { return action.get(); } finally { CURRENT.remove(); }
    }
    private long elapsed() { return Math.max(0,(clock.getAsLong()-start)/1_000_000); }
    public static <T> T stage(String name,Supplier<T> action) {
        var timing=CURRENT.get();
        if(timing==null)return action.get();
        long began=timing.clock.getAsLong();boolean failed=true;
        try {var result=action.get();failed=false;return result;}
        finally {
            long duration=Math.max(0,(timing.clock.getAsLong()-began)/1_000_000);
            var prior=timing.stages.getOrDefault(name,new Stage(0,0,0));
            timing.stages.put(name,new Stage(prior.durationMs()+duration,prior.count()+1,prior.failedCount()+(failed?1:0)));
        }
    }
    static void generationStarting() {var t=CURRENT.get();if(t!=null&&t.preGenerationMs==null)t.preGenerationMs=t.elapsed();}
    public static void targetDispatching() {var t=CURRENT.get();if(t!=null&&t.targetDispatchMs==null)t.targetDispatchMs=t.elapsed();}
    public static void firstToken() {var t=CURRENT.get();if(t!=null&&t.firstTokenMs==null)t.firstTokenMs=t.elapsed();}
    static void bodyReady() { var t=CURRENT.get();if(t!=null&&t.bodyReadyMs==null)t.bodyReadyMs=t.elapsed(); }
    static void reviewed() { var t=CURRENT.get();if(t!=null)t.reviewCompleteMs=t.elapsed(); }
    public void processingComplete() { processingCompleteMs=elapsed(); }
    public Snapshot snapshot() { return new Snapshot("QA_SERVER",bodyReadyMs,reviewCompleteMs,processingCompleteMs,
            preGenerationMs,targetDispatchMs,Collections.unmodifiableMap(new LinkedHashMap<>(stages)),firstTokenMs); }
}
