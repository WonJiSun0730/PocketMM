package com.example.projectmm.api.world;

import com.example.projectmm.application.dialogue.*;
import com.example.projectmm.infrastructure.dialogue.OpenAiDialogueGateway;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/worlds/{worldId}/rooms/{roomId}/sessions/{sessionId}")
public class PocketReplySuggestionController {
    private final ReplySuggestionScene scenes;
    private final ReplySuggestionStore store;
    private final OpenAiDialogueGateway openai;
    public record Request(Long messageId,Integer variantNo,@Size(max=100000) String content) {}

    @PostMapping("/reply-suggestions")
    public ReplySuggestionStore.Result suggest(@PathVariable Long worldId,@PathVariable Long roomId,
            @PathVariable Long sessionId,@Valid @RequestBody Request request,Authentication authentication,
            HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control","no-store");
        var scene=scenes.prepare(worldId,roomId,sessionId,authentication.getName(),
                new ReplySuggestionScene.Source(request.messageId(),request.variantNo(),request.content()));
        return store.getOrCreate(scene,()->openai.suggestPlayerReplies(sessionId,scene.evidence()));
    }
}
