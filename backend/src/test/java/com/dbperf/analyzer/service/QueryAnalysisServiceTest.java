package com.dbperf.analyzer.service;

import com.dbperf.ai.AiQueryAnalysis;
import com.dbperf.ai.QueryAnalysisAi;
import com.dbperf.analyzer.domain.QueryAnalysis;
import com.dbperf.analyzer.dto.AnalyzeRequest;
import com.dbperf.analyzer.dto.QueryAnalysisResponse;
import com.dbperf.analyzer.repository.QueryAnalysisRepository;
import com.dbperf.common.exception.InvalidRequestException;
import com.dbperf.connection.service.ConnectionAccess;
import com.dbperf.connection.service.TargetConnectionFactory;
import com.dbperf.privacy.dto.SanitizedPayload;
import com.dbperf.privacy.dto.ValidationResult;
import com.dbperf.privacy.service.SanitizationService;
import com.dbperf.secrets.SecretStore;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryAnalysisServiceTest {

    @Mock
    private QueryAnalysisAi ai;
    @Mock
    private TargetSchemaInspector schemaInspector;
    @Mock
    private ConnectionAccess connectionAccess;
    @Mock
    private TargetConnectionFactory connectionFactory;
    @Mock
    private SecretStore secretStore;
    @Mock
    private QueryAnalysisRepository analysisRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private SanitizationService sanitizationService;

    private QueryAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new QueryAnalysisService(ai, new AnalysisPromptBuilder(), schemaInspector,
                connectionAccess, connectionFactory, secretStore, analysisRepository,
                currentUserService, sanitizationService, new ObjectMapper());
    }

    /** By default the privacy gate passes the payload through unchanged. */
    private void allowSanitization() {
        when(sanitizationService.enforceForAnalysis(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> new SanitizedPayload(invocation.getArgument(2),
                        invocation.getArgument(3), invocation.getArgument(4),
                        List.of(), List.of(), ValidationResult.clean()));
    }

    private AiQueryAnalysis sampleAnalysis() {
        return new AiQueryAnalysis(
                "The query seq-scans orders because customer_id is unindexed.",
                List.of(new AiQueryAnalysis.Issue("HIGH", "MISSING_INDEX",
                        "No index on orders.customer_id forces a full table scan")),
                List.of(new AiQueryAnalysis.Recommendation("Add index on orders.customer_id",
                        "Turns the seq scan into an index scan",
                        "CREATE INDEX CONCURRENTLY idx_orders_customer_id ON orders (customer_id);",
                        "~50x on this query")),
                "SELECT ...",
                "20-60x faster",
                null);
    }

    @Test
    void rejectsRequestWithNeitherSqlNorPlan() {
        assertThatThrownBy(() -> service.analyze(new AnalyzeRequest(null, " ", null, false)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(ai);
    }

    @Test
    void analyzesWithoutConnectionAndPersistsResult() {
        allowSanitization();
        when(ai.analyze(anyString(), anyString())).thenReturn(sampleAnalysis());
        when(ai.model()).thenReturn("claude-opus-4-8");
        when(currentUserService.require()).thenReturn(
                User.builder().id(UUID.randomUUID()).email("j@e.com").passwordHash("h")
                        .fullName("J").role(Role.USER).build());
        when(analysisRepository.saveAndFlush(any(QueryAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QueryAnalysisResponse response = service.analyze(new AnalyzeRequest(
                null, "SELECT * FROM orders WHERE customer_id = 42", null, false));

        assertThat(response.analysis().issues()).hasSize(1);
        assertThat(response.analysis().issues().get(0).type()).isEqualTo("MISSING_INDEX");

        ArgumentCaptor<QueryAnalysis> captor = ArgumentCaptor.forClass(QueryAnalysis.class);
        verify(analysisRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getResultJson()).contains("MISSING_INDEX");
        assertThat(captor.getValue().getSummary()).contains("seq-scans orders");
        assertThat(captor.getValue().getModel()).isEqualTo("claude-opus-4-8");
        // no connection — schema inspection must not run
        verifyNoInteractions(connectionFactory);
    }

    @Test
    void promptContainsPastedExplainOutput() {
        allowSanitization();
        when(ai.analyze(anyString(), anyString())).thenReturn(sampleAnalysis());
        when(ai.model()).thenReturn("claude-opus-4-8");
        when(currentUserService.require()).thenReturn(
                User.builder().id(UUID.randomUUID()).email("j@e.com").passwordHash("h")
                        .fullName("J").role(Role.USER).build());
        when(analysisRepository.saveAndFlush(any(QueryAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.analyze(new AnalyzeRequest(null, null, "Seq Scan on orders (actual time=120..340)", false));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ai).analyze(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("## Execution plan")
                .contains("Seq Scan on orders (actual time=120..340)");
    }
}
