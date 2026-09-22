package com.example.projectmm.presentation.chat;

import com.example.projectmm.application.chat.ChatHistoryResponse;
import com.example.projectmm.application.chat.ChatRequest;
import com.example.projectmm.application.chat.ChatResponse;
import com.example.projectmm.application.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/{characterId}")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable Long characterId,
            @RequestBody ChatRequest request
    ) {
        ChatResponse response =
                chatService.chat(
                        characterId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{characterId}/messages")
    public ResponseEntity<List<ChatHistoryResponse>> getRecentMessages(
            @PathVariable Long characterId
    ) {
        return ResponseEntity.ok(
                chatService.getRecentMessages(characterId)
        );
    }
}