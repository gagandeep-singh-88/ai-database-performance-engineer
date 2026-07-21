package com.dbperf.copilot.service;

import com.dbperf.ai.AiChatMessage;
import com.dbperf.ai.AiCopilotReply;
import com.dbperf.ai.QueryAnalysisAi;
import com.dbperf.analyzer.dto.AnalysisHistoryItem;
import com.dbperf.analyzer.repository.QueryAnalysisRepository;
import com.dbperf.common.exception.ResourceNotFoundException;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.service.ConnectionAccess;
import com.dbperf.copilot.domain.ChatMessage;
import com.dbperf.copilot.domain.ChatSession;
import com.dbperf.copilot.dto.ChatMessageDto;
import com.dbperf.copilot.dto.ChatRequest;
import com.dbperf.copilot.dto.ChatResponse;
import com.dbperf.copilot.dto.ChatSessionDetail;
import com.dbperf.copilot.dto.ChatSessionSummary;
import com.dbperf.copilot.repository.ChatMessageRepository;
import com.dbperf.copilot.repository.ChatSessionRepository;
import com.dbperf.dashboard.service.HealthScoreCalculator;
import com.dbperf.metrics.domain.MetricSnapshot;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.repository.MetricSnapshotRepository;
import com.dbperf.metrics.service.MetricsCollectorService;
import com.dbperf.privacy.service.SanitizationService;
import com.dbperf.user.domain.User;
import com.dbperf.user.service.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotService {

    /** Prior turns sent back to the model; older turns are dropped, not summarized. */
    private static final int HISTORY_WINDOW = 20;

    private final QueryAnalysisAi ai;
    private final CopilotPromptBuilder promptBuilder;
    private final ConnectionAccess connectionAccess;
    private final MetricSnapshotRepository snapshotRepository;
    private final MetricsCollectorService collectorService;
    private final HealthScoreCalculator healthScoreCalculator;
    private final QueryAnalysisRepository analysisRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final CurrentUserService currentUserService;
    private final SanitizationService sanitizationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChatResponse chat(ChatRequest request) {
        User user = currentUserService.require();
        UUID userId = user.getId();
        ChatSession session = resolveSession(request, userId);

        // Fresh grounding on every turn so the copilot always sees current metrics
        Grounding grounding = ground(session.getConnectionId(), userId);

        // Privacy gate: redact + validate the grounding context and the user's
        // message before either reaches the AI or gets persisted — same gate
        // the Query Analyzer goes through, so the user's AI & Privacy settings
        // are enforced on every AI entry point, not just one.
        SanitizationService.CopilotPayload safe = sanitizationService.enforceForCopilot(
                user, grounding.context(), request.message());

        List<AiChatMessage> history = new ArrayList<>();
        history.add(AiChatMessage.user(safe.groundingContext()));
        history.add(AiChatMessage.assistant(
                "Understood. I have the metrics context and will ground my answers in it."));
        List<ChatMessage> prior = messageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.getId());
        prior.stream().skip(Math.max(0, prior.size() - HISTORY_WINDOW)).forEach(message ->
                history.add(message.getRole() == ChatMessage.Role.USER
                        ? AiChatMessage.user(message.getContent())
                        : AiChatMessage.assistant(message.getContent())));
        history.add(AiChatMessage.user(safe.userMessage()));

        AiCopilotReply reply = ai.structuredChat(promptBuilder.systemPrompt(), history, AiCopilotReply.class);

        messageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(ChatMessage.Role.USER)
                .content(safe.userMessage())
                .build());
        ChatMessage assistantMessage = messageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(ChatMessage.Role.ASSISTANT)
                .content(reply.reply())
                .followUpsJson(toJson(reply.suggestedFollowUps()))
                .model(ai.model())
                .build());
        session.setUpdatedAt(Instant.now());

        return new ChatResponse(session.getId(), reply.reply(), reply.suggestedFollowUps(),
                ai.model(), grounding.capturedAt(), assistantMessage.getCreatedAt());
    }

    public List<ChatSessionSummary> sessions(int limit) {
        UUID userId = currentUserService.require().getId();
        return sessionRepository
                .findAllByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 100)))
                .stream()
                .map(ChatSessionSummary::from)
                .toList();
    }

    public ChatSessionDetail session(UUID id) {
        ChatSession session = requireOwnedSession(id);
        List<ChatMessageDto> messages = messageRepository
                .findAllBySessionIdOrderByCreatedAtAsc(session.getId())
                .stream()
                .map(message -> new ChatMessageDto(message.getId(), message.getRole().name(),
                        message.getContent(), fromJson(message.getFollowUpsJson()),
                        message.getModel(), message.getCreatedAt()))
                .toList();
        return new ChatSessionDetail(ChatSessionSummary.from(session), messages);
    }

    @Transactional
    public void delete(UUID id) {
        sessionRepository.delete(requireOwnedSession(id));
    }

    private ChatSession resolveSession(ChatRequest request, UUID userId) {
        if (request.sessionId() != null) {
            return requireOwnedSession(request.sessionId());
        }
        if (request.connectionId() != null) {
            connectionAccess.requireOwned(request.connectionId());
        }
        String title = request.message().strip().replaceAll("\\s+", " ");
        return sessionRepository.save(ChatSession.builder()
                .userId(userId)
                .connectionId(request.connectionId())
                .title(title.length() > 120 ? title.substring(0, 120) + "…" : title)
                .build());
    }

    private ChatSession requireOwnedSession(UUID id) {
        UUID userId = currentUserService.require().getId();
        return sessionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
    }

    private record Grounding(String context, Instant capturedAt) {
    }

    private Grounding ground(UUID connectionId, UUID userId) {
        if (connectionId == null) {
            return new Grounding(promptBuilder.ungroundedContext(), null);
        }
        DatabaseConnection connection = connectionAccess.requireOwned(connectionId);
        return snapshotRepository.findFirstByConnectionIdOrderByCapturedAtDesc(connectionId)
                .map((MetricSnapshot snapshot) -> {
                    SnapshotDetailResponse latest = collectorService.toDetail(snapshot);
                    List<AnalysisHistoryItem> recent = analysisRepository
                            .findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 5))
                            .stream()
                            .map(AnalysisHistoryItem::from)
                            .toList();
                    return new Grounding(
                            promptBuilder.groundingContext(connection,
                                    healthScoreCalculator.assess(latest), latest, recent),
                            latest.summary().capturedAt());
                })
                .orElseGet(() -> new Grounding("""
                        ## Metrics context
                        Connection "%s" (database "%s") is configured, but the collector has not \
                        captured a snapshot yet. Metrics arrive within ~5 minutes of adding a \
                        connection. Tell the user to check back shortly, and answer general \
                        PostgreSQL questions in the meantime.""".formatted(
                                connection.getName(), connection.getDatabaseName()),
                        null));
    }

    private String toJson(List<String> followUps) {
        try {
            return followUps == null ? null : objectMapper.writeValueAsString(followUps);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize follow-ups", e);
        }
    }

    private List<String> fromJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Corrupt follow-ups JSON, ignoring: {}", e.getMessage());
            return List.of();
        }
    }
}
