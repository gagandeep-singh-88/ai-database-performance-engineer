package com.dbperf.ai;

import com.dbperf.common.exception.AiUnavailableException;

/** Placeholder when no AI provider is configured — fails fast with setup guidance. */
public class UnconfiguredQueryAnalysisAi implements QueryAnalysisAi {

    private static final String MESSAGE =
            "AI is not configured. Set ANTHROPIC_API_KEY (Claude) or GEMINI_API_KEY (Google Gemini), "
                    + "or use a local model with AI_PROVIDER=ollama (requires a running Ollama server), "
                    + "then restart.";

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String model() {
        return "none";
    }

    @Override
    public AiQueryAnalysis analyze(String systemPrompt, String userPrompt) {
        throw new AiUnavailableException(MESSAGE);
    }

    @Override
    public <T> T structured(String systemPrompt, String userPrompt, Class<T> type) {
        throw new AiUnavailableException(MESSAGE);
    }
}
