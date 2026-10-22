package com.example.projectmm.qa;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class QaAtomicFilesTest {
    @TempDir Path directory;
    @Test void transientWindowsReadLockRetriesOnlyRenameAndPreservesCompleteFile() throws Exception {
        var source=Files.writeString(directory.resolve("new.tmp"),"new complete budget");
        var target=Files.writeString(directory.resolve("budget.json"),"prior budget");
        var attempts=new AtomicInteger();
        QaAtomicFiles.replace(source,target,(a,b)->{
            if(attempts.incrementAndGet()<3)throw new AccessDeniedException(b.toString());
            Files.move(a,b,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        });
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(Files.readString(target)).isEqualTo("new complete budget");
        assertThat(source).doesNotExist();
    }
    @Test void permanentFailureStopsWithOldBudgetIntact() throws Exception {
        var source=Files.writeString(directory.resolve("new.tmp"),"new");
        var target=Files.writeString(directory.resolve("budget.json"),"old");
        var attempts=new AtomicInteger();
        assertThatThrownBy(()->QaAtomicFiles.replace(source,target,(a,b)->{attempts.incrementAndGet();throw new AccessDeniedException(b.toString());}))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(attempts.get()).isEqualTo(5);
        assertThat(Files.readString(target)).isEqualTo("old");
    }
    @Test void unsupportedAtomicMoveFailsWithoutNonAtomicFallback() {
        var attempts=new AtomicInteger();
        assertThatThrownBy(()->QaAtomicFiles.replace(directory.resolve("new"),directory.resolve("old"),(a,b)->{
            attempts.incrementAndGet();throw new AtomicMoveNotSupportedException(a.toString(),b.toString(),"fixture");
        })).isInstanceOf(AtomicMoveNotSupportedException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }
}
