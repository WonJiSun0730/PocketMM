package com.example.projectmm.application.dialogue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DialogueTimingTest {
    @Test void milestonesUseMonotonicClockAndNeverInventUnreachedStages() {
        var clock=new AtomicLong(100_000_000);
        var timing=new DialogueTiming(clock::get);
        timing.measure(()->{
            clock.set(200_000_000);DialogueTiming.bodyReady();
            clock.set(250_000_000);DialogueTiming.bodyReady();
            clock.set(600_000_000);DialogueTiming.reviewed();
            return null;
        });
        assertThat(timing.snapshot().bodyReadyMs()).isEqualTo(100);
        assertThat(timing.snapshot().reviewCompleteMs()).isEqualTo(500);
        assertThat(timing.snapshot().processingCompleteMs()).isNull();
        clock.set(900_000_000);timing.processingComplete();
        assertThat(timing.snapshot().processingCompleteMs()).isEqualTo(800);
    }
    @Test void exceptionClearsScopeAndPreservesOnlyObservedStages() {
        var timing=new DialogueTiming(()->0);
        assertThatThrownBy(()->timing.measure(()->{DialogueTiming.bodyReady();throw new IllegalStateException();}));
        DialogueTiming.reviewed();
        assertThat(timing.snapshot().bodyReadyMs()).isZero();
        assertThat(timing.snapshot().reviewCompleteMs()).isNull();
        var next=new DialogueTiming(()->0);
        next.measure(()->{DialogueTiming.reviewed();return null;});
        assertThat(next.snapshot().reviewCompleteMs()).isZero();
        assertThat(timing.snapshot().reviewCompleteMs()).isNull();
    }
}
