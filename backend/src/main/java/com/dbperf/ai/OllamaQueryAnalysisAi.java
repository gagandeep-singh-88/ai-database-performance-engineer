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
 * Local-LLM implementation via Ollama's REST API (e.g. qwen2.5:1.5b).
 * Output validity is enforced with grammar-constrained decoding: the JSON
 * schema derived from the target record is passed as Ollama's format
 * field, so even small models emit schema-valid JSON. Runs fully offline
 * at zero cost — quality depends on the local model chosen.
 */
@Slf4j
public class OllamaQueryAnalysisAi implements QueryAnalysisAi {

    private final AiProperties properties;
    private final ObjectMapper lenientMapper;
    private final RestClient http;

    public OllamaQueryAnalysisAi(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.lenientMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        // local models on CPU can be slow — be generous
        requestFactory.setReadTimeout(Duration.ofSeconds(600));
        this.http = RestClient.builder()
                .baseUrl(properties.ollamaBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String model() {
        return "ollama/" + properties.ollamaModel();
    }

    @Override
    public AiQueryAnalysis analyze(String systemPrompt, String userPrompt) {
        return structured(systemPrompt, userPrompt, AiQueryAnalysis.class);
    }

    @Override
    public <T> T structured(String systemPrompt, String userPrompt, Class<T> type) {
        Map<String, Object> body = Map.of(
                "model", properties.ollamaModel(),
                "stream", false,
                "format", JsonSchemaGenerator.schemaFor(type),
                "options", Map.of("temperature", 0.2, "num_predict", properties.maxTokens()),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        try {
            long start = System.currentTimeMillis();
            JsonNode response = http.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String content = response == null ? null
                    : response.path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new AiUnavailableException("Ollama returned no content");
            }
            log.info("Ollama {} ({}) completed in {} ms", type.getSimpleName(),
                    properties.ollamaModel(), System.currentTimeMillis() - start);
            return GeminiQueryAnalysisAi.parseJson(lenientMapper, content, type);
        } catch (RestClientResponseException e) {
            String message = ollamaError(e);
            log.warn("Ollama error ({}): {}", e.getStatusCode().value(), message);
            throw new AiUnavailableException("AI analysis failed: " + message
                    + (message.contains("not found")
                    ? " — pull it with: ollama pull " + properties.ollamaModel() : ""));
        } catch (ResourceAccessException e) {
            throw new AiUnavailableException("Cannot reach Ollama at " + properties.ollamaBaseUrl()
                    + " — is `ollama serve` running? (" + e.getMessage() + ")");
        }
    }

    private String ollamaError(RestClientResponseException e) {
        try {
            String message = lenientMapper.readTree(e.getResponseBodyAsString()).path("error").asText(null);
            if (message != null) {
                return message;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return e.getStatusText();
    }
}
