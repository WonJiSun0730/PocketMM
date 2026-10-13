package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.PocketProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/worlds/pockets/{sessionId}/progress")
public class PocketProgressController {
    private final PocketProgressService progress;
    public record Configuration(boolean enabled, Integer minimumAbsenceMinutes) {}
    public record Simulation(int hours) {}
    @GetMapping public PocketProgressService.Status current(@PathVariable Long sessionId, Authentication auth) {
        return progress.current(sessionId, auth.getName());
    }
    @PutMapping public PocketProgressService.Status configure(@PathVariable Long sessionId,
            @RequestBody Configuration request, Authentication auth) {
        return progress.configure(sessionId, auth.getName(), request.enabled(), request.minimumAbsenceMinutes());
    }
    @PostMapping("/resume") public PocketProgressService.Status resume(@PathVariable Long sessionId, Authentication auth) {
        return progress.resume(sessionId, auth.getName());
    }
    @PostMapping("/simulate") public PocketProgressService.Status simulate(@PathVariable Long sessionId,
            @RequestBody Simulation request, Authentication auth) {
        return progress.simulateAbsence(sessionId, auth.getName(), request.hours());
    }
    @DeleteMapping("/reports/{reportId}") public PocketProgressService.Status discard(@PathVariable Long sessionId,
            @PathVariable Long reportId, Authentication auth) {
        return progress.discardLatest(sessionId,auth.getName(),reportId);
    }
    @PostMapping("/presence") public void presence(@PathVariable Long sessionId, Authentication auth) {
        progress.presence(sessionId, auth.getName());
    }
}
