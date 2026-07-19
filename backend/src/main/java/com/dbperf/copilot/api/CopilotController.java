package com.dbperf.copilot.api;

import com.dbperf.copilot.dto.ChatRequest;
import com.dbperf.copilot.dto.ChatResponse;
import com.dbperf.copilot.dto.ChatSessionDetail;
import com.dbperf.copilot.dto.ChatSessionSummary;
import com.dbperf.copilot.service.CopilotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/copilot")
@RequiredArgsConstructor
@Tag(name = "AI Copilot", description = "Chat grounded in the collected performance metrics")
@SecurityRequirement(name = "bearerAuth")
public class CopilotController {

    private final CopilotService copilotService;

    @PostMapping("/chat")
    @Operation(summary = "Send a message; each turn is re-grounded in the latest metric snapshot")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(copilotService.chat(request));
    }

    @GetMapping("/sessions")
    @Operation(summary = "Chat sessions, most recently active first")
    public ResponseEntity<List<ChatSessionSummary>> sessions(@RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(copilotService.sessions(limit));
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Full session transcript")
    public ResponseEntity<ChatSessionDetail> session(@PathVariable UUID id) {
        return ResponseEntity.ok(copilotService.session(id));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "Delete a session and its messages")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        copilotService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
