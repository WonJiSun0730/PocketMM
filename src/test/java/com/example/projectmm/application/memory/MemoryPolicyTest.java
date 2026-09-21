package com.example.projectmm.application.memory;

import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.memory.MemoryDecision;
import com.example.projectmm.domain.scene.Scene;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPolicyTest {

    private final MemoryPolicy memoryPolicy =
            new MemoryPolicy();

    @Test
    void 중요한_이벤트는_기억으로_남긴다() {

        Scene scene =
                new Scene(
                        LocalDateTime.now(),
                        "테스트",
                        "DAILY"
                );

        Event event =
                new Event(
                        scene,
                        LocalDateTime.now(),
                        "INTERACTION",
                        "중요한 대화를 나누었다.",
                        7
                );

        MemoryDecision decision =
                memoryPolicy.evaluate(event);

        assertThat(decision.memorable())
                .isTrue();

        assertThat(decision.strength())
                .isEqualTo(70);
    }

    @Test
    void 중요도가_낮은_이벤트는_기억하지_않는다() {

        Scene scene =
                new Scene(
                        LocalDateTime.now(),
                        "테스트",
                        "DAILY"
                );

        Event event =
                new Event(
                        scene,
                        LocalDateTime.now(),
                        "REST",
                        "잠시 쉬었다.",
                        2
                );

        MemoryDecision decision =
                memoryPolicy.evaluate(event);

        assertThat(decision.memorable())
                .isFalse();

        assertThat(decision.strength())
                .isZero();
    }
}