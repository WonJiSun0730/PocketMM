package com.example.projectmm.presentation.chat;

import com.example.projectmm.application.chat.ChatHistoryResponse;
import com.example.projectmm.application.chat.ChatRequest;
import com.example.projectmm.application.chat.ChatResponse;
import com.example.projectmm.application.chat.ChatService;
import com.example.projectmm.application.chat.ConversationStartService;
import com.example.projectmm.application.chat.NewConversationResponse;
import com.example.projectmm.application.chat.ConversationProgressService;
import com.example.projectmm.application.player.ConversationPlayerService;
import com.example.projectmm.application.player.PlayerSelectionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ConversationStartService conversationStartService;
    private final ConversationPlayerService conversationPlayerService;
    private final ConversationProgressService progressService;

    public record ProgressRequest(@jakarta.validation.constraints.NotNull Boolean enabled) {}

    @GetMapping("/{characterId}/conversations/{conversationId}/progress")
    public ConversationProgressService.Status progress(@PathVariable Long characterId, @PathVariable Long conversationId) {
        return progressService.current(characterId, conversationId);
    }

    @PostMapping("/{characterId}/conversations/{conversationId}/resume")
    public ConversationProgressService.Status resume(@PathVariable Long characterId, @PathVariable Long conversationId) {
        return progressService.resume(characterId, conversationId);
    }

    @PutMapping("/{characterId}/conversations/{conversationId}/auto-progress")
    public ConversationProgressService.Status configureProgress(@PathVariable Long characterId,
            @PathVariable Long conversationId, @Valid @RequestBody ProgressRequest request) {
        return progressService.configure(characterId, conversationId, request.enabled());
    }

    @PostMapping("/{characterId}/conversations/{conversationId}/presence")
    public ResponseEntity<Void> presence(@PathVariable Long characterId, @PathVariable Long conversationId) {
        progressService.heartbeat(characterId, conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{characterId}/conversations")
    public ResponseEntity<NewConversationResponse> startConversation(@PathVariable Long characterId,
            @Valid @RequestBody PlayerSelectionRequest request) {
        return ResponseEntity.status(201).body(conversationStartService.start(characterId, request.playerProfileId()));
    }

    @GetMapping("/{characterId}/session")
    public ConversationPlayerService.Session session(@PathVariable Long characterId) {
        // 기존 입장 경로와 동일하게 방·인트로를 준비한 뒤 선택 정보를 조회한다.
        chatService.getRecentMessages(characterId);
        return conversationPlayerService.current(characterId);
    }

    @PutMapping("/{characterId}/conversations/{conversationId}/player-profile")
    public ConversationPlayerService.Session selectPlayer(@PathVariable Long characterId,
            @PathVariable Long conversationId, @Valid @RequestBody PlayerSelectionRequest request) {
        return conversationPlayerService.select(characterId, conversationId, request.playerProfileId());
    }

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
