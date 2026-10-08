package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.DialogueIssueService;
import com.example.projectmm.domain.chat.DialogueIssue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.LocalDateTime;

@RestController @RequestMapping("/api/worlds")
public class DialogueIssueController {
    private final DialogueIssueService service;
    public DialogueIssueController(DialogueIssueService service) { this.service = service; }
    public record Request(@NotNull @Min(1) Integer variantNo,
            @NotBlank @Pattern(regexp = "TIME|CAST|CHARACTER|PLAYER_CONTROL|PROSE|OOC|OTHER") String category,
            @NotBlank @Size(max = 5000) String problem, @NotBlank @Size(max = 5000) String expected) {}
    public record Item(Long id, Long sessionId, Long messageId, int variantNo, String category,
                       String problem, String expected, String evidenceJson, LocalDateTime createdAt) {
        static Item from(DialogueIssue issue) {
            return new Item(issue.getId(), issue.getSessionId(), issue.getMessageId(), issue.getVariantNo(),
                    issue.getCategory(), issue.getProblem(), issue.getExpected(), issue.getEvidenceJson(), issue.getCreatedAt());
        }
    }
    @PostMapping("/pockets/{sessionId}/messages/{messageId}/issues") @ResponseStatus(HttpStatus.CREATED)
    public Item save(@PathVariable Long sessionId, @PathVariable Long messageId, @Valid @RequestBody Request request,
                     Authentication auth) {
        return Item.from(service.save(sessionId, messageId, request.variantNo(), auth.getName(),
                request.category(), request.problem(), request.expected()));
    }
    @GetMapping("/dialogue-issues")
    public List<Item> list(@RequestParam(required = false) Long before, Authentication auth) {
        return service.list(auth.getName(), before).stream().map(Item::from).toList();
    }
    @DeleteMapping("/dialogue-issues/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { service.delete(id, auth.getName()); }
}
