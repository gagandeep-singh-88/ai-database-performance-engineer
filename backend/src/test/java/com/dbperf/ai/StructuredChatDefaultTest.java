package com.dbperf.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The interface default must flatten history so non-Anthropic providers work unchanged. */
class StructuredChatDefaultTest {

    private record Reply(String text) {
    }

    @Test
    void defaultFlattensHistoryIntoSinglePrompt() {
        StringBuilder captured = new StringBuilder();
        QueryAnalysisAi ai = new QueryAnalysisAi() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public String model() {
                return "test";
            }

            @Override
            public AiQueryAnalysis analyze(String systemPrompt, String userPrompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T structured(String systemPrompt, String userPrompt, Class<T> type) {
                captured.append(userPrompt);
                return (T) new Reply("ok");
            }
        };

        Reply reply = ai.structuredChat("system", List.of(
                AiChatMessage.user("context block"),
                AiChatMessage.assistant("acknowledged"),
                AiChatMessage.user("what is slow?")), Reply.class);

        assertThat(reply.text()).isEqualTo("ok");
        assertThat(captured.toString())
                .contains("User: context block")
                .contains("Assistant: acknowledged")
                .contains("## Latest user message (respond to this)\nwhat is slow?");
    }
}
