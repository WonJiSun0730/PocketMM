package com.example.projectmm.application.dialogue;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Server milestones only; never a browser receive/paint measurement. */
public final class DialogueTiming {
    private static final ThreadLocal<DialogueTiming> CURRENT = new ThreadLocal<>();
    private final LongSupplier clock;
    private final long start;
    private Long bodyReadyMs, reviewCompleteMs, processingCompleteMs;
    public record Snapshot(String source, Long bodyReadyMs, Long reviewCompleteMs, Long processingCompleteMs) {}
    public DialogueTiming() { this(System::nanoTime); }
    public DialogueTiming(LongSupplier clock) { this.clock=clock;start=clock.getAsLong(); }
    public <T> T measure(Supplier<T> action) {
        if(CURRENT.get()!=null)throw new IllegalStateException("Nested dialogue timing");
        CURRENT.set(this);
        try { return action.get(); } finally { CURRENT.remove(); }
    }
    private long elapsed() { return Math.max(0,(clock.getAsLong()-start)/1_000_000); }
    static void bodyReady() { var t=CURRENT.get();if(t!=null&&t.bodyReadyMs==null)t.bodyReadyMs=t.elapsed(); }
    static void reviewed() { var t=CURRENT.get();if(t!=null)t.reviewCompleteMs=t.elapsed(); }
    public void processingComplete() { processingCompleteMs=elapsed(); }
    public Snapshot snapshot() { return new Snapshot("QA_SERVER",bodyReadyMs,reviewCompleteMs,processingCompleteMs); }
}
