package com.dbperf.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.dbperf.common.exception.AiUnavailableException;
import com.dbperf.config.AiProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Claude-backed analyst using the official Anthropic Java SDK.
 * Uses structured outputs (schema derived from {@link AiQueryAnalysis}),
 * so the API guarantees the response deserializes into our record, and
 * adaptive thinking so Claude reasons about plans before answering.
 * Instantiated by {@link com.dbperf.config.AiConfig} when an Anthropic
 * key is configured.
 */
@Slf4j
public class AnthropicQueryAnalysisAi implements QueryAnalysisAi {

    private final AiProperties properties;
    private final AnthropicClient client;

    public AnthropicQueryAnalysisAi(AiProperties properties) {
        this.properties = properties;
        this.client = properties.hasAnthropicKey()
                ? AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build()
                : null;
    }

    @Override
    public boolean isConfigured() {
        return client != null;
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public AiQueryAnalysis analyze(String systemPrompt, String userPrompt) {
        return structured(systemPrompt, userPrompt, AiQueryAnalysis.class);
    }

    @Override
    public <T> T structured(String systemPrompt, String userPrompt, Class<T> type) {
        return execute(baseParams(systemPrompt, type).addUserMessage(userPrompt).build(), type);
    }

    /** Native multi-turn chat: history maps to real user/assistant messages instead of a flattened prompt. */
    @Override
    public <T> T structuredChat(String systemPrompt, java.util.List<AiChatMessage> history, Class<T> type) {
        StructuredMessageCreateParams.Builder<T> builder = baseParams(systemPrompt, type);
        for (AiChatMessage message : history) {
            if (message.role() == AiChatMessage.Role.USER) {
                builder.addUserMessage(message.content());
            } else {
                builder.addAssistantMessage(message.content());
            }
        }
        return execute(builder.build(), type);
    }

    private <T> StructuredMessageCreateParams.Builder<T> baseParams(String systemPrompt, Class<T> type) {
        if (client == null) {
            throw new AiUnavailableException(
                    "AI analysis is not configured. Set the ANTHROPIC_API_KEY environment variable and restart.");
        }
        return MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(systemPrompt)
                .outputConfig(type);
    }

    private <T> T execute(StructuredMessageCreateParams<T> params, Class<T> type) {
        try {
            long start = System.currentTimeMillis();
            T result = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(typed -> typed.text())
                    .orElseThrow(() -> new AiUnavailableException("Claude returned no analysable content"));
            log.info("Claude {} completed in {} ms", type.getSimpleName(), System.currentTimeMillis() - start);
            return result;
        } catch (AnthropicServiceException e) {
            log.warn("Claude API error ({}): {}", e.statusCode(), e.getMessage());
            throw new AiUnavailableException("AI analysis failed: " + humanMessage(e));
        }
    }

    /** Pull the human-readable message out of the API error body instead of surfacing raw JSON. */
    private static String humanMessage(AnthropicServiceException e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"message\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(raw);
        return matcher.find() ? matcher.group(1) : raw;
    }
}
