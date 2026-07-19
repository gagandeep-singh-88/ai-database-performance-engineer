package com.dbperf.ai;

import java.util.List;

/**
 * Boundary to the LLM. Kept as an interface so services can be unit-tested
 * with a stub and so the underlying provider/model can change freely.
 */
public interface QueryAnalysisAi {

    boolean isConfigured();

    /** @return the model id used for analyses (for persistence/display). */
    String model();

    AiQueryAnalysis analyze(String systemPrompt, String userPrompt);

    /** Generic structured-output call: Claude's response is guaranteed to deserialize into {@code type}. */
    <T> T structured(String systemPrompt, String userPrompt, Class<T> type);

    /**
     * Multi-turn structured chat. The default flattens the conversation into a
     * single prompt so every provider works unchanged; providers with native
     * multi-turn support (Anthropic) override for real message history.
     * {@code history} must end with the latest user message.
     */
    default <T> T structuredChat(String systemPrompt, List<AiChatMessage> history, Class<T> type) {
        StringBuilder prompt = new StringBuilder("## Conversation so far\n");
        for (int i = 0; i < history.size() - 1; i++) {
            AiChatMessage message = history.get(i);
            prompt.append(message.role() == AiChatMessage.Role.USER ? "User: " : "Assistant: ")
                    .append(message.content()).append("\n\n");
        }
        prompt.append("## Latest user message (respond to this)\n")
                .append(history.get(history.size() - 1).content());
        return structured(systemPrompt, prompt.toString(), type);
    }
}
