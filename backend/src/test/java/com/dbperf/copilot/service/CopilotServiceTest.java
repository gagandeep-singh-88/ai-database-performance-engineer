package com.dbperf.copilot.service;

import com.dbperf.ai.AiChatMessage;
import com.dbperf.ai.AiCopilotReply;
import com.dbperf.ai.QueryAnalysisAi;
import com.dbperf.analyzer.repository.QueryAnalysisRepository;
import com.dbperf.common.exception.ResourceNotFoundException;
import com.dbperf.connection.domain.DatabaseConnection;
import com.dbperf.connection.service.ConnectionAccess;
import com.dbperf.copilot.domain.ChatMessage;
import com.dbperf.copilot.domain.ChatSession;
import com.dbperf.copilot.dto.ChatRequest;
import com.dbperf.copilot.dto.ChatResponse;
import com.dbperf.copilot.repository.ChatMessageRepository;
import com.dbperf.copilot.repository.ChatSessionRepository;
import com.dbperf.dashboard.service.HealthScoreCalculator;
import com.dbperf.metrics.domain.MetricSnapshot;
import com.dbperf.metrics.dto.QueryStat;
import com.dbperf.metrics.dto.SnapshotDetailResponse;
import com.dbperf.metrics.dto.SnapshotSummaryResponse;
import com.dbperf.metrics.dto.TableStat;
import com.dbperf.metrics.repository.MetricSnapshotRepository;
import com.dbperf.metrics.service.MetricsCollectorService;
import com.dbperf.privacy.service.SanitizationService;
import com.dbperf.user.domain.Role;
import com.dbperf.user.domain.User;
import com.dbperf.user.service.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopilotServiceTest {

    @Mock
    private QueryAnalysisAi ai;
    @Mock
    private ConnectionAccess connectionAccess;
    @Mock
    private MetricSnapshotRepository snapshotRepository;
    @Mock
    private MetricsCollectorService collectorService;
    @Mock
    private QueryAnalysisRepository analysisRepository;
    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private SanitizationService sanitizationService;

    private final UUID userId = UUID.randomUUID();
    private CopilotService service;

    @BeforeEach
    void setUp() {
        service = new CopilotService(ai, new CopilotPromptBuilder(), connectionAccess,
                snapshotRepository, collectorService, new HealthScoreCalculator(),
                analysisRepository, sessionRepository, messageRepository,
                currentUserService, sanitizationService, new ObjectMapper());
        lenient().when(currentUserService.require()).thenReturn(
                User.builder().id(userId).email("j@e.com").passwordHash("h")
                        .fullName("J").role(Role.USER).build());
        // By default the privacy gate passes the payload through unchanged.
        lenient().when(sanitizationService.enforceForCopilot(any(), any(), any()))
                .thenAnswer(invocation -> new SanitizationService.CopilotPayload(
                        invocation.getArgument(1), invocation.getArgument(2)));
        lenient().when(sessionRepository.save(any(ChatSession.class)))
                .thenAnswer(invocation -> {
                    ChatSession session = invocation.getArgument(0);
                    if (session.getId() == null) session.setId(UUID.randomUUID());
                    return session;
                });
        lenient().when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(ai.structuredChat(anyString(), anyList(), eq(AiCopilotReply.class)))
                .thenReturn(new AiCopilotReply("The orders table is seq-scanned heavily.",
                        List.of("Which index should I add first?")));
        lenient().when(ai.model()).thenReturn("claude-opus-4-8");
    }

    private SnapshotDetailResponse snapshotDetail() {
        return new SnapshotDetailResponse(
                new SnapshotSummaryResponse(UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                        42_000_000L, 3, 0, 0, 0, 0.99, 1000, 5, 0, 0),
                List.of(new QueryStat("1", "SELECT * FROM orders WHERE status = $1", 900, 45_000, 50.0, 100, 90, 10, 0.9)),
                List.of(), List.of(),
                List.of(new TableStat("orders", 12_000, 9_000_000, 40, 250_000)));
    }

    @Test
    void groundsNewChatInLatestSnapshot() {
        UUID connectionId = UUID.randomUUID();
        DatabaseConnection connection = mock(DatabaseConnection.class);
        when(connection.getName()).thenReturn("prod-db");
        when(connection.getDatabaseName()).thenReturn("shop");
        when(connectionAccess.requireOwned(connectionId)).thenReturn(connection);
        MetricSnapshot snapshot = mock(MetricSnapshot.class);
        when(snapshotRepository.findFirstByConnectionIdOrderByCapturedAtDesc(connectionId))
                .thenReturn(Optional.of(snapshot));
        when(collectorService.toDetail(snapshot)).thenReturn(snapshotDetail());
        when(analysisRepository.findAllByUserIdOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(List.of());
        when(messageRepository.findAllBySessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        ChatResponse response = service.chat(new ChatRequest(null, connectionId, "What is slow?"));

        assertThat(response.reply()).contains("orders");
        assertThat(response.suggestedFollowUps()).hasSize(1);
        assertThat(response.groundedAt()).isNotNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessage>> historyCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(ai).structuredChat(anyString(), historyCaptor.capture(), eq(AiCopilotReply.class));
        List<AiChatMessage> history = historyCaptor.getValue();
        assertThat(history.get(0).content())
                .contains("prod-db")
                .contains("Health score")
                .contains("SELECT * FROM orders")
                .contains("orders: ~250,000 rows");
        assertThat(history.get(history.size() - 1).content()).isEqualTo("What is slow?");

        // both turns persisted
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues())
                .extracting(ChatMessage::getRole)
                .containsExactly(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT);
    }

    @Test
    void chatsUngroundedWhenNoConnectionSelected() {
        when(messageRepository.findAllBySessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        ChatResponse response = service.chat(new ChatRequest(null, null, "How do indexes work?"));

        assertThat(response.groundedAt()).isNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessage>> historyCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(ai).structuredChat(anyString(), historyCaptor.capture(), eq(AiCopilotReply.class));
        assertThat(historyCaptor.getValue().get(0).content()).contains("No database connection is selected");
    }

    @Test
    void explainsWhenCollectorHasNoSnapshotYet() {
        UUID connectionId = UUID.randomUUID();
        DatabaseConnection connection = mock(DatabaseConnection.class);
        when(connection.getName()).thenReturn("prod-db");
        when(connection.getDatabaseName()).thenReturn("shop");
        when(connectionAccess.requireOwned(connectionId)).thenReturn(connection);
        when(snapshotRepository.findFirstByConnectionIdOrderByCapturedAtDesc(connectionId))
                .thenReturn(Optional.empty());
        when(messageRepository.findAllBySessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        ChatResponse response = service.chat(new ChatRequest(null, connectionId, "What is slow?"));

        assertThat(response.groundedAt()).isNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessage>> historyCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(ai).structuredChat(anyString(), historyCaptor.capture(), eq(AiCopilotReply.class));
        assertThat(historyCaptor.getValue().get(0).content()).contains("not captured a snapshot yet");
    }

    @Test
    void continuingSessionReplaysPriorTurns() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(
                ChatSession.builder().id(sessionId).userId(userId).title("t").build()));
        when(messageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(
                ChatMessage.builder().sessionId(sessionId).role(ChatMessage.Role.USER).content("earlier question").build(),
                ChatMessage.builder().sessionId(sessionId).role(ChatMessage.Role.ASSISTANT).content("earlier answer").build()));

        service.chat(new ChatRequest(sessionId, null, "follow-up"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessage>> historyCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(ai).structuredChat(anyString(), historyCaptor.capture(), eq(AiCopilotReply.class));
        List<AiChatMessage> history = historyCaptor.getValue();
        assertThat(history)
                .extracting(AiChatMessage::content)
                .containsSubsequence("earlier question", "earlier answer", "follow-up");
    }

    @Test
    void privacyGateBlocksChatBeforeCallingAiOrPersisting() {
        when(sanitizationService.enforceForCopilot(any(), any(), any()))
                .thenThrow(new com.dbperf.common.exception.SensitiveDataException(
                        "AI is disabled in your privacy settings, so this request was not sent to the AI."));

        assertThatThrownBy(() -> service.chat(new ChatRequest(null, null, "SELECT * FROM customers")))
                .isInstanceOf(com.dbperf.common.exception.SensitiveDataException.class);

        verify(ai, org.mockito.Mockito.never()).structuredChat(anyString(), anyList(), eq(AiCopilotReply.class));
        verify(messageRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void sanitizedTextIsSentToAiAndPersistedInsteadOfRawInput() {
        when(messageRepository.findAllBySessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(sanitizationService.enforceForCopilot(any(), any(), any()))
                .thenReturn(new SanitizationService.CopilotPayload(
                        "## Metrics context (sanitized)", "redacted message with $1 in place of the email"));

        service.chat(new ChatRequest(null, null, "my email is john@example.com"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatMessage>> historyCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(ai).structuredChat(anyString(), historyCaptor.capture(), eq(AiCopilotReply.class));
        assertThat(historyCaptor.getValue()).extracting(AiChatMessage::content)
                .doesNotContain("john@example.com")
                .contains("redacted message with $1 in place of the email");

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues())
                .extracting(ChatMessage::getContent)
                .doesNotContain("my email is john@example.com");
    }

    @Test
    void rejectsAccessToForeignSession() {
        UUID foreignSession = UUID.randomUUID();
        when(sessionRepository.findByIdAndUserId(foreignSession, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.session(foreignSession))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
