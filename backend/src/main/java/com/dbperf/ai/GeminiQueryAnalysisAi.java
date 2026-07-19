package com.dbperf.ai;

import com.dbperf.common.exception.AiUnavailableException;
import com.dbperf.config.AiProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini implementation of the AI boundary via the Generative
 * Language REST API — no extra SDK dependency. Structured output is
 * enforced with responseMimeType=application/json plus an explicit JSON
 * shape derived from the target record, then parsed leniently.
 */
@Slf4j
public class GeminiQueryAnalysisAi implements QueryAnalysisAi {

    private final AiProperties properties;
    private final ObjectMapper lenientMapper;
    private final RestClient http;

    public GeminiQueryAnalysisAi(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.lenientMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(180));
        this.http = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String model() {
        return properties.geminiModel();
    }

    @Override
    public AiQueryAnalysis analyze(String systemPrompt, String userPrompt) {
        return structured(systemPrompt, userPrompt, AiQueryAnalysis.class);
    }

    @Override
    public <T> T structured(String systemPrompt, String userPrompt, Class<T> type) {
        String prompt = userPrompt
                + "\n\nRespond with a SINGLE JSON object exactly matching this shape"
                + " (no markdown fences, no commentary):\n"
                + JsonShapeDescriber.describe(type);

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "maxOutputTokens", properties.maxTokens(),
                        "temperature", 0.2));

        try {
            long start = System.currentTimeMillis();
            JsonNode response = http.post()
                    .uri("/models/{model}:generateContent", properties.geminiModel())
                    .header("x-goog-api-key", properties.geminiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String text = response == null ? null
                    : response.path("candidates").path(0).path("content").path("parts").path(0)
                    .path("text").asText(null);
            if (text == null) {
                String reason = response == null ? "empty response"
                        : response.path("candidates").path(0).path("finishReason")
                        .asText(response.path("promptFeedback").path("blockReason").asText("unknown"));
                throw new AiUnavailableException("Gemini returned no content (" + reason + ")");
            }
            log.info("Gemini {} completed in {} ms", type.getSimpleName(), System.currentTimeMillis() - start);
            return parseJson(lenientMapper, text, type);
        } catch (RestClientResponseException e) {
            String message = apiErrorMessage(e);
            log.warn("Gemini API error ({}): {}", e.getStatusCode().value(), message);
            throw new AiUnavailableException("AI analysis failed: " + message);
        } catch (ResourceAccessException e) {
            throw new AiUnavailableException("Cannot reach the Gemini API: " + e.getMessage());
        }
    }

    /** Parse model output into the target type, tolerating markdown fences. */
    static <T> T parseJson(ObjectMapper mapper, String text, Class<T> type) {
        String cleaned = text.strip();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).strip();
            }
        }
        try {
            return mapper.readValue(cleaned, type);
        } catch (Exception e) {
            throw new AiUnavailableException("AI returned unparseable output: " + e.getMessage());
        }
    }

    private String apiErrorMessage(RestClientResponseException e) {
        try {
            JsonNode node = lenientMapper.readTree(e.getResponseBodyAsString());
            String message = node.path("error").path("message").asText(null);
            if (message != null) {
                return message;
            }
        } catch (Exception ignored) {
            // fall through to raw message
        }
        return e.getStatusText();
    }
}
