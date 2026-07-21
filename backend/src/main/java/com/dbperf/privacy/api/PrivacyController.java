package com.dbperf.privacy.api;

import com.dbperf.privacy.dto.AuditLogItem;
import com.dbperf.privacy.dto.PayloadPreviewRequest;
import com.dbperf.privacy.dto.PayloadPreviewResponse;
import com.dbperf.privacy.dto.PrivacySettingsDto;
import com.dbperf.privacy.dto.UpdatePrivacySettingsRequest;
import com.dbperf.privacy.service.PrivacySettingsService;
import com.dbperf.privacy.service.SanitizationAuditService;
import com.dbperf.privacy.service.SanitizationService;
import com.dbperf.user.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/privacy")
@RequiredArgsConstructor
@Tag(name = "Privacy & Sanitization", description = "Inspect and control what is sent to the AI")
@SecurityRequirement(name = "bearerAuth")
public class PrivacyController {

    private final SanitizationService sanitizationService;
    private final PrivacySettingsService settingsService;
    private final SanitizationAuditService auditService;
    private final CurrentUserService currentUserService;

    @PostMapping("/preview")
    @Operation(summary = "Preview the exact sanitized payload that would be sent to the AI")
    public ResponseEntity<PayloadPreviewResponse> preview(@RequestBody PayloadPreviewRequest request) {
        return ResponseEntity.ok(sanitizationService.preview(request));
    }

    @GetMapping("/settings")
    @Operation(summary = "Current user's privacy settings")
    public ResponseEntity<PrivacySettingsDto> settings() {
        return ResponseEntity.ok(settingsService.current());
    }

    @PutMapping("/settings")
    @Operation(summary = "Update SQL sanitization / AI-enabled toggles")
    public ResponseEntity<PrivacySettingsDto> updateSettings(
            @RequestBody UpdatePrivacySettingsRequest request) {
        return ResponseEntity.ok(settingsService.update(request));
    }

    @GetMapping("/audit")
    @Operation(summary = "Sanitization audit trail (types and outcomes only — never raw values)")
    public ResponseEntity<List<AuditLogItem>> audit(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(auditService.list(currentUserService.require().getId(), limit));
    }
}
