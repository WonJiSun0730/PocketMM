package com.example.projectmm.application.dialogue;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class DialogueTokensTest {
    @Test void onlyTargetScopePublishesAndFirstTimeIsNotOverwrittenOrLeakedAfterFailure() {
        var clock=new AtomicLong();var timing=new DialogueTiming(clock::get);var values=new ArrayList<String>();
        assertThatThrownBy(()->timing.measure(()->DialogueTokens.deliver(values::add,()->{
            DialogueTokens.publish("bootstrap");
            return DialogueTokens.generate(()->{
                clock.set(4_000_000);DialogueTokens.publish("a");
                clock.set(9_000_000);DialogueTokens.publish("ab");throw new IllegalStateException();
            });
        })));
        assertThat(values).containsExactly("a","ab");assertThat(timing.snapshot().firstTokenMs()).isEqualTo(4);
        assertThat(timing.snapshot().bodyReadyMs()).isNull();
        assertThat(DialogueTokens.enabled()).isFalse();
        DialogueTokens.generate(()->{DialogueTokens.publish("later");return null;});
        assertThat(values).containsExactly("a","ab");
        assertThat(new DialogueTiming().snapshot().firstTokenMs()).isNull();
    }
}
