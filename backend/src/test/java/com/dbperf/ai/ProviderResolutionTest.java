package com.dbperf.ai;

import com.dbperf.config.AiProperties;
import com.dbperf.config.AiProperties.ResolvedProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderResolutionTest {

    private AiProperties props(String provider, String anthropicKey, String geminiKey) {
        return new AiProperties(provider, anthropicKey, geminiKey,
                "claude-opus-4-8", "gemini-3.5-flash", "http://localhost:11434", "qwen2.5:1.5b", 16000);
    }

    @Test
    void autoPrefersAnthropicWhenBothKeysPresent() {
        assertThat(props("auto", "sk-ant-x", "AIza-x").resolveProvider())
                .isEqualTo(ResolvedProvider.ANTHROPIC);
    }

    @Test
    void autoFallsBackToGeminiWhenOnlyGeminiKeyPresent() {
        assertThat(props("auto", "", "AIza-x").resolveProvider())
                .isEqualTo(ResolvedProvider.GEMINI);
    }

    @Test
    void autoResolvesNoneWithoutAnyKey() {
        assertThat(props("auto", "", "").resolveProvider()).isEqualTo(ResolvedProvider.NONE);
    }

    @Test
    void explicitGeminiWinsOverPresentAnthropicKey() {
        assertThat(props("gemini", "sk-ant-x", "AIza-x").resolveProvider())
                .isEqualTo(ResolvedProvider.GEMINI);
    }

    @Test
    void explicitOllamaNeedsNoKeyAndIsNeverAutoSelected() {
        assertThat(props("ollama", "", "").resolveProvider()).isEqualTo(ResolvedProvider.OLLAMA);
        // auto never picks ollama, even with no other keys
        assertThat(props("auto", "", "").resolveProvider()).isEqualTo(ResolvedProvider.NONE);
    }

    @Test
    void explicitProviderWithoutItsKeyResolvesNone() {
        assertThat(props("gemini", "sk-ant-x", "").resolveProvider())
                .isEqualTo(ResolvedProvider.NONE);
        assertThat(props("anthropic", "", "AIza-x").resolveProvider())
                .isEqualTo(ResolvedProvider.NONE);
    }
}
