package com.example.projectmm.application.dialogue;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Delivery only: a preview never approves or persists a generated turn. */
public final class DialoguePreview {
    private DialoguePreview() {}
    private static final ThreadLocal<Consumer<String>> listener = new ThreadLocal<>();

    public static <T> T deliver(Consumer<String> consumer, Supplier<T> action) {
        if (listener.get() != null) throw new IllegalStateException("Nested dialogue preview");
        listener.set(consumer);
        try { return action.get(); }
        finally { listener.remove(); }
    }

    static void generated(String reply) {
        var consumer = listener.get();
        if (consumer != null) consumer.accept(reply);
    }
}
