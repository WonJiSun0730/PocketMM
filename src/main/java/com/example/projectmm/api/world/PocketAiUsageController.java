package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.infrastructure.dialogue.AiUsageTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/worlds/pockets/{sessionId}/ai-usage")
public class PocketAiUsageController {

    private final WorldRoomSessionService sessions;
    private final AiUsageTracker usage;

    @GetMapping
    public AiUsageTracker.Snapshot latest(@PathVariable Long sessionId, Authentication authentication) {
        sessions.pocket(sessionId, authentication.getName());
        return usage.latest(sessionId);
    }
    @GetMapping("/calls")
    public java.util.List<AiUsageTracker.Call> calls(@PathVariable Long sessionId, Authentication authentication) {
        sessions.pocket(sessionId, authentication.getName());
        return usage.calls(sessionId);
    }
    @GetMapping("/prompt-diagnostics")
    public AiUsageTracker.PromptDiagnostics promptDiagnostics(@PathVariable Long sessionId, Authentication authentication) {
        sessions.pocket(sessionId, authentication.getName());
        return usage.promptDiagnostics(sessionId);
    }
}
