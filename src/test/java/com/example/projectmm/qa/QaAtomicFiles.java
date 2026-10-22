package com.example.projectmm.qa;

import java.io.IOException;
import java.nio.file.*;

/** Retry only the local atomic rename, never an HTTP request. */
final class QaAtomicFiles {
    interface Move { void replace(Path source, Path target) throws IOException; }
    static void replace(Path source, Path target) throws IOException {
        replace(source, target, (a, b) -> Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }
    static void replace(Path source, Path target, Move move) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try { move.replace(source, target); return; }
            catch (AccessDeniedException error) {
                if (attempt >= 4) throw error;
                try { Thread.sleep(25L << attempt); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("QA local file replacement interrupted", interrupted);
                }
            }
        }
    }
}
