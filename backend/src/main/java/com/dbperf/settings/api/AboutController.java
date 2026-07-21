package com.dbperf.settings.api;

import com.dbperf.settings.dto.AboutResponse;
import com.dbperf.settings.service.AboutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Application build/runtime info for the About tab")
@SecurityRequirement(name = "bearerAuth")
public class AboutController {

    private final AboutService aboutService;

    @GetMapping("/about")
    @Operation(summary = "Application name, versions, AI provider, and useful links")
    public ResponseEntity<AboutResponse> about() {
        return ResponseEntity.ok(aboutService.about());
    }
}
