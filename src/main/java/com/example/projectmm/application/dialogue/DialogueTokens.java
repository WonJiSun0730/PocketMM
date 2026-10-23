package com.example.projectmm.application.dialogue;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Only the checked target generation can publish provisional text, never bootstrap/review. */
public final class DialogueTokens {
    private static final ThreadLocal<Boolean> generating = new ThreadLocal<>();
    private static final ThreadLocal<Consumer<String>> listener = new ThreadLocal<>();
    private DialogueTokens() {}
    public static boolean enabled() { return Boolean.TRUE.equals(generating.get()); }
    public static <T> T generate(Supplier<T> work) {
        var previous=generating.get();generating.set(true);
        try { return work.get(); } finally { if(previous==null)generating.remove();else generating.set(previous); }
    }
    public static <T> T deliver(Consumer<String> consumer,Supplier<T> work) {
        if(listener.get()!=null)throw new IllegalStateException("Nested token delivery");
        listener.set(consumer);
        try {return work.get();} finally {listener.remove();}
    }
    public static void publish(String cumulativeReply) {
        if(!enabled() || cumulativeReply.isEmpty())return;
        DialogueTiming.firstToken();
        var consumer=listener.get();
        if(consumer!=null)consumer.accept(cumulativeReply);
    }
}
