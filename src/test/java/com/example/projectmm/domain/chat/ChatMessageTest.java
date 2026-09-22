package com.example.projectmm.domain.chat;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.character.Character;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageTest {

    @Test
    void 채팅_메시지를_생성할_수_있다() {
        Character millie = new Character("Millie");
        LocalDateTime now = LocalDateTime.now();

        ChatMessage message =
                new ChatMessage(
                        millie,
                        ChatRole.USER,
                        "오늘 뭐 했어?",
                        ContentMode.NORMAL,
                        now
                );

        assertThat(message.getCharacter()).isEqualTo(millie);
        assertThat(message.getRole()).isEqualTo(ChatRole.USER);
        assertThat(message.getContent()).isEqualTo("오늘 뭐 했어?");
        assertThat(message.getContentMode()).isEqualTo(ContentMode.NORMAL);
        assertThat(message.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void 빈_메시지는_허용하지_않는다() {
        Character millie = new Character("Millie");

        org.assertj.core.api.Assertions
                .assertThatThrownBy(
                        () -> new ChatMessage(
                                millie,
                                ChatRole.USER,
                                "   ",
                                ContentMode.NORMAL,
                                LocalDateTime.now()
                        )
                )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }
}