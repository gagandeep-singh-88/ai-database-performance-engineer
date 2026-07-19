package com.dbperf.config;

import com.dbperf.ai.AnthropicQueryAnalysisAi;
import com.dbperf.ai.GeminiQueryAnalysisAi;
import com.dbperf.ai.OllamaQueryAnalysisAi;
import com.dbperf.ai.QueryAnalysisAi;
import com.dbperf.ai.UnconfiguredQueryAnalysisAi;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects the AI provider from configuration: Claude, Gemini, or a helpful 503. */
@Slf4j
@Configuration
public class AiConfig {

    @Bean
    public QueryAnalysisAi queryAnalysisAi(AiProperties properties, ObjectMapper objectMapper) {
        return switch (properties.resolveProvider()) {
            case ANTHROPIC -> {
                log.info("AI provider: Anthropic Claude ({})", properties.model());
                yield new AnthropicQueryAnalysisAi(properties);
            }
            case GEMINI -> {
                log.info("AI provider: Google Gemini ({})", properties.geminiModel());
                yield new GeminiQueryAnalysisAi(properties, objectMapper);
            }
            case OLLAMA -> {
                log.info("AI provider: Ollama local model ({} at {})",
                        properties.ollamaModel(), properties.ollamaBaseUrl());
                yield new OllamaQueryAnalysisAi(properties, objectMapper);
            }
            case NONE -> {
                log.warn("AI provider: NOT CONFIGURED — set ANTHROPIC_API_KEY or GEMINI_API_KEY to enable AI features");
                yield new UnconfiguredQueryAnalysisAi();
            }
        };
    }
}
