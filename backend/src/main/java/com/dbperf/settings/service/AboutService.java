package com.dbperf.settings.service;

import com.dbperf.config.AiProperties;
import com.dbperf.settings.dto.AboutResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.stereotype.Service;

/**
 * Static build/runtime info for the Settings &gt; About tab. Everything here
 * is either injected config or a JVM/framework fact — nothing is persisted.
 */
@Service
@RequiredArgsConstructor
public class AboutService {

    private final AiProperties aiProperties;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${app.version}")
    private String appVersion;

    @Value("${app.build-version}")
    private String buildVersion;

    public AboutResponse about() {
        return new AboutResponse(
                appName,
                appVersion,
                buildVersion,
                System.getProperty("java.version"),
                SpringBootVersion.getVersion(),
                aiProviderLabel(),
                aiModelLabel(),
                "Google Cloud Run",
                new AboutResponse.Links(
                        "https://github.com/gagandeep-singh-88/ai-database-performance-engineer/issues",
                        "https://github.com/gagandeep-singh-88/ai-database-performance-engineer#readme",
                        "https://github.com/gagandeep-singh-88/ai-database-performance-engineer/blob/main/LICENSE"));
    }

    private String aiProviderLabel() {
        return switch (aiProperties.resolveProvider()) {
            case ANTHROPIC -> "Claude (Anthropic)";
            case GEMINI -> "Gemini (Google)";
            case OLLAMA -> "Ollama (local)";
            case NONE -> "Not configured";
        };
    }

    private String aiModelLabel() {
        return switch (aiProperties.resolveProvider()) {
            case ANTHROPIC -> aiProperties.model();
            case GEMINI -> aiProperties.geminiModel();
            case OLLAMA -> aiProperties.ollamaModel();
            case NONE -> "—";
        };
    }
}
