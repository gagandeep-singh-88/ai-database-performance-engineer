package com.dbperf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Multi-provider AI configuration.
 *
 * @param provider      "auto" (Anthropic if keyed, else Gemini if keyed),
 *                      "anthropic", "gemini", or "ollama" (local, explicit only)
 * @param apiKey        Anthropic API key
 * @param geminiApiKey  Google Gemini API key
 * @param model         Claude model id
 * @param geminiModel   Gemini model id
 * @param ollamaBaseUrl Ollama server URL
 * @param ollamaModel   Ollama model tag, e.g. qwen2.5:1.5b
 * @param maxTokens     per-response output cap
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String provider,
        String apiKey,
        String geminiApiKey,
        String model,
        String geminiModel,
        String ollamaBaseUrl,
        String ollamaModel,
        long maxTokens) {

    public boolean hasAnthropicKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasGeminiKey() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    public enum ResolvedProvider { ANTHROPIC, GEMINI, OLLAMA, NONE }

    /**
     * Which provider should serve requests. Ollama is never auto-selected
     * (no key to detect, and quality varies with the local model) — it must
     * be requested explicitly via AI_PROVIDER=ollama.
     */
    public ResolvedProvider resolveProvider() {
        String requested = provider == null ? "auto" : provider.trim().toLowerCase();
        return switch (requested) {
            case "anthropic" -> hasAnthropicKey() ? ResolvedProvider.ANTHROPIC : ResolvedProvider.NONE;
            case "gemini" -> hasGeminiKey() ? ResolvedProvider.GEMINI : ResolvedProvider.NONE;
            case "ollama" -> ResolvedProvider.OLLAMA;
            default -> hasAnthropicKey() ? ResolvedProvider.ANTHROPIC
                    : hasGeminiKey() ? ResolvedProvider.GEMINI
                    : ResolvedProvider.NONE;
        };
    }
}
