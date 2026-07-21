package com.dbperf.analyzer.service;

import com.dbperf.ai.AiQueryAnalysis;
import com.dbperf.ai.QueryAnalysisAi;
import com.dbperf.analyzer.domain.QueryAnalysis;
import com.dbperf.analyzer.dto.AnalysisHistoryItem;
import com.dbperf.analyzer.dto.AnalyzeRequest;
import com.dbperf.analyzer.dto.QueryAnalysisResponse;
import com.dbperf.analyzer.repository.QueryAnalysisRepository;
import com.dbperf.common.exception.InvalidRequestException;
import com.dbperf.common.exception.ResourceNotFoundException;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.service.ConnectionAccess;
import com.dbperf.connection.service.TargetConnectionFactory;
import com.dbperf.privacy.dto.SanitizedPayload;
import com.dbperf.privacy.service.SanitizationService;
import com.dbperf.secrets.SecretStore;
import com.dbperf.user.domain.User;
import com.dbperf.user.service.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryAnalysisService {

    private final QueryAnalysisAi ai;
    private final AnalysisPromptBuilder promptBuilder;
    private final TargetSchemaInspector schemaInspector;
    private final ConnectionAccess connectionAccess;
    private final TargetConnectionFactory connectionFactory;
    private final SecretStore secretStore;
    private final QueryAnalysisRepository analysisRepository;
    private final CurrentUserService currentUserService;
    private final SanitizationService sanitizationService;
    private final ObjectMapper objectMapper;

    public QueryAnalysisResponse analyze(AnalyzeRequest request) {
        if (!request.hasSql() && !request.hasExplainOutput()) {
            throw new InvalidRequestException("Provide SQL, EXPLAIN output, or both");
        }

        String plan = request.hasExplainOutput() ? request.explainOutput().strip() : null;
        String schemaContext = null;

        // Ground the analysis in the live target when a connection is given
        if (request.connectionId() != null) {
            DatabaseConnection target = connectionAccess.requireOwned(request.connectionId());
            String password = secretStore.retrieve(target.getSecretRef());
            try (Connection connection = connectionFactory.open(target.getHost(), target.getPort(),
                    target.getDatabaseName(), target.getUsername(), password, target.getSslMode())) {
                if (request.hasSql()) {
                    if (plan == null) {
                        plan = schemaInspector.explain(connection, request.sql(), request.runAnalyze());
                    }
                    schemaContext = schemaInspector.schemaContext(connection, request.sql());
                }
            } catch (SQLException e) {
                // target unreachable — analyze with whatever we have rather than failing
                log.info("Target unavailable for grounding, analyzing without it: {}", e.getMessage());
            }
        }

        // Privacy gate: redact + validate everything before it can reach the AI.
        // Only the sanitized text is sent to Claude AND persisted at rest.
        User user = currentUserService.require();
        String rawSql = request.hasSql() ? request.sql().strip() : null;
        SanitizedPayload safe = sanitizationService.enforceForAnalysis(user, null, rawSql, plan, schemaContext);

        AiQueryAnalysis analysis = ai.analyze(
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(safe.sql(), safe.plan(), safe.schemaContext()));

        QueryAnalysis entity = analysisRepository.saveAndFlush(QueryAnalysis.builder()
                .userId(user.getId())
                .connectionId(request.connectionId())
                .sqlText(safe.sql())
                .planText(safe.plan())
                .summary(analysis.summary())
                .resultJson(toJson(analysis))
                .model(ai.model())
                .build());

        return new QueryAnalysisResponse(entity.getId(), entity.getConnectionId(), entity.getSqlText(),
                safe.plan(), safe.schemaContext(), analysis, entity.getModel(), entity.getCreatedAt());
    }

    public List<AnalysisHistoryItem> history(int limit) {
        UUID userId = currentUserService.require().getId();
        return analysisRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 200)))
                .stream()
                .map(AnalysisHistoryItem::from)
                .toList();
    }

    public QueryAnalysisResponse get(UUID id) {
        UUID userId = currentUserService.require().getId();
        QueryAnalysis entity = analysisRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found"));
        return new QueryAnalysisResponse(entity.getId(), entity.getConnectionId(), entity.getSqlText(),
                entity.getPlanText(), null, fromJson(entity.getResultJson()), entity.getModel(),
                entity.getCreatedAt());
    }

    private String toJson(AiQueryAnalysis analysis) {
        try {
            return objectMapper.writeValueAsString(analysis);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize analysis", e);
        }
    }

    private AiQueryAnalysis fromJson(String json) {
        try {
            return objectMapper.readValue(json, AiQueryAnalysis.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt stored analysis", e);
        }
    }
}
