package com.example.projectmm.application.dialogue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DialogueTimingTest {
    @Test void nestedAndRepeatedStageDurationsPreserveResultsFailuresAndImmutableSnapshots() {
        var clock=new AtomicLong();var timing=new DialogueTiming(clock::get);
        timing.measure(()->{
            int value=DialogueTiming.stage("CONTEXT_BUILD",()->{
                clock.set(1_000_000);
                DialogueTiming.stage("CONTEXT_MEMORY_RECALL",()->{clock.set(4_000_000);return "memory";});
                clock.set(7_000_000);return 42;
            });
            assertThat(value).isEqualTo(42);
            var before=timing.snapshot();
            assertThatThrownBy(()->DialogueTiming.stage("CONTEXT_MEMORY_RECALL",()->{clock.set(9_000_000);throw new IllegalStateException("failure");}));
            assertThat(before.stages().get("CONTEXT_MEMORY_RECALL").count()).isEqualTo(1);
            DialogueTiming.generationStarting();
            clock.set(12_000_000);DialogueTiming.targetDispatching();
            return null;
        });
        var snapshot=timing.snapshot();
        assertThat(snapshot.preGenerationMs()).isEqualTo(9);
        assertThat(snapshot.targetDispatchMs()).isEqualTo(12);
        assertThat(snapshot.stages().get("CONTEXT_BUILD").durationMs()).isEqualTo(7);
        assertThat(snapshot.stages().get("CONTEXT_MEMORY_RECALL").durationMs()).isEqualTo(5);
        assertThat(snapshot.stages().get("CONTEXT_MEMORY_RECALL").count()).isEqualTo(2);
        assertThat(snapshot.stages().get("CONTEXT_MEMORY_RECALL").failedCount()).isEqualTo(1);
        assertThatThrownBy(()->snapshot.stages().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(DialogueTiming.stage("outside",()->23)).isEqualTo(23);
        assertThat(timing.snapshot().stages()).doesNotContainKey("outside");
    }
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
