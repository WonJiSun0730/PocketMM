package com.example.projectmm.application.dialogue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ReplySuggestionStoreTest {
    @TempDir Path directory;
    final ReplySuggestionScene.Snapshot scene=new ReplySuggestionScene.Snapshot(1L,"a".repeat(64),"evidence");
    @Test void restartAndToggleReuseSavedSuggestionsWithoutCallingModel() throws Exception {
        var calls=new AtomicInteger();
        var store=new ReplySuggestionStore(directory.toString());
        var first=store.getOrCreate(scene,()->{calls.incrementAndGet();return List.of("one","two","three");});
        var restarted=new ReplySuggestionStore(directory.toString());
        assertThat(restarted.getOrCreate(scene,()->{throw new AssertionError("duplicate paid call");})).isEqualTo(first);
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void concurrentRequestSeesReservationAndCannotDoubleCharge() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var store=new ReplySuggestionStore(directory.toString());
        try(var executor=Executors.newSingleThreadExecutor()) {
            var first=executor.submit(()->store.getOrCreate(scene,()->{
                entered.countDown();try {release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IllegalStateException(e);}
                return List.of("one","two","three");
            }));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(store.getOrCreate(scene,()->{throw new AssertionError("duplicate");}).status()).isEqualTo("PENDING");
            } finally {release.countDown();}
            assertThat(first.get(5,TimeUnit.SECONDS).status()).isEqualTo("READY");
        }
    }
    @Test void failedPaidAttemptRemainsFailedWithoutAutomaticRetry() throws Exception {
        var store=new ReplySuggestionStore(directory.toString());
        assertThat(store.getOrCreate(scene,()->{throw new IllegalStateException("provider failure");}).status()).isEqualTo("FAILED");
        assertThat(store.getOrCreate(scene,()->{throw new AssertionError("retry");}).status()).isEqualTo("FAILED");
    }
    @Test void differentPocketAndPassageHaveIndependentCache() throws Exception {
        var store=new ReplySuggestionStore(directory.toString());
        store.getOrCreate(scene,()->List.of("one","two","three"));
        var other=new ReplySuggestionScene.Snapshot(2L,scene.key(),"other pocket");
        assertThat(store.getOrCreate(other,()->List.of("four","five","six")).suggestions()).containsExactly("four","five","six");
        var next=new ReplySuggestionScene.Snapshot(1L,"b".repeat(64),"next passage");
        assertThat(store.getOrCreate(next,()->List.of("seven","eight","nine")).suggestions()).containsExactly("seven","eight","nine");
    }
    @Test void oldFailureIsExplainedWithoutChargingAgain() throws Exception {
        var folder=directory.resolve("1");java.nio.file.Files.createDirectories(folder);
        java.nio.file.Files.writeString(folder.resolve(scene.key()+".json"),
                "{\"sceneKey\":\""+scene.key()+"\",\"status\":\"FAILED\",\"suggestions\":[]}");
        var result=new ReplySuggestionStore(directory.toString()).getOrCreate(scene,()->{throw new AssertionError("paid retry");});
        assertThat(result.errorCode()).isEqualTo("LEGACY_FAILURE");
    }
    @Test void generatedPayloadSurvivesParserFailureButGenericExceptionMessagesAreNotSaved() throws Exception {
        var store=new ReplySuggestionStore(directory.toString());
        var result=store.getOrCreate(scene,()->{throw new ReplySuggestions.Failure("INVALID_JSON","invalid draft","generated drafts",null);});
        assertThat(result.errorCode()).isEqualTo("INVALID_JSON");
        assertThat(java.nio.file.Files.readString(directory.resolve("1").resolve(scene.key()+".failure.json"))).contains("generated drafts");
        var other=new ReplySuggestionScene.Snapshot(2L,scene.key(),"private prompt");
        store.getOrCreate(other,()->{throw new IllegalStateException("SECRET provider message");});
        assertThat(java.nio.file.Files.readString(directory.resolve("2").resolve(scene.key()+".failure.json")))
                .doesNotContain("SECRET","private prompt");
    }
    @Test void keepsPartialSuggestionsInsteadOfReportingTotalFailure() throws Exception {
        var result=new ReplySuggestionStore(directory.toString()).getOrCreate(scene,()->List.of("first","second"));
        assertThat(result.status()).isEqualTo("PARTIAL");assertThat(result.suggestions()).hasSize(2);
    }
}
