package com.example.projectmm.presentation.state;

import com.example.projectmm.application.state.CustomStateService;
import com.example.projectmm.application.state.CustomStateSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.List;

@RestController
@RequestMapping("/api/conversations/{conversationId}/custom-states")
@RequiredArgsConstructor
public class CustomStateController {
    private final CustomStateService service;

    public record UpdateRequest(@NotNull JsonNode currentValue) {
        // JSON 문자열 "80" 등의 숫자 강제 변환을 허용하지 않는다. 범위 검증은 도메인 책임이다.
        @AssertTrue(message = "currentValue must be a JSON number")
        public boolean isCurrentValueNumber() {
            return currentValue == null || currentValue.isNumber();
        }
    }

    @GetMapping
    public List<CustomStateSnapshot> current(@PathVariable Long conversationId) {
        return service.current(conversationId);
    }

    @PatchMapping("/{definitionId}")
    public CustomStateSnapshot update(@PathVariable Long conversationId, @PathVariable Long definitionId,
                                      @Valid @RequestBody UpdateRequest request) {
        try {
            return service.updateByUser(conversationId, definitionId, request.currentValue().doubleValue());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
