package com.dbperf.privacy;

import com.dbperf.privacy.repository.SanitizationAuditRepository;
import com.dbperf.user.domain.Role;
import com.dbperf.user.domain.User;
import com.dbperf.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack test of the Privacy module: HTTP → security → controller →
 * services (real sanitizers/validator) → JPA (H2). Verifies the payload is
 * masked, the audit trail is persisted, and settings gate the AI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivacyIntegrationTest {

    private static final String EMAIL = "privacy-int@acme.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SanitizationAuditRepository auditRepository;

    @BeforeEach
    void seedUser() {
        userRepository.findByEmailIgnoreCase(EMAIL).orElseGet(() -> userRepository.save(
                User.builder().email(EMAIL).passwordHash("x").fullName("Privacy Tester")
                        .role(Role.USER).build()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void previewMasksSensitiveDataAndWritesAudit() throws Exception {
        long before = auditRepository.count();

        mockMvc.perform(post("/api/v1/privacy/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sql":"SELECT * FROM customers WHERE email='john@example.com'"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacyStatus").value("PROTECTED"))
                .andExpect(jsonPath("$.validation.passed").value(true))
                .andExpect(jsonPath("$.sanitized.sql").value(
                        "SELECT * FROM customers WHERE email='$1'"))
                .andExpect(jsonPath("$.findings[0].type").value("EMAIL"))
                .andExpect(jsonPath("$.placeholders[0].placeholder").value("$1"))
                .andExpect(jsonPath("$.placeholders[0].category").value("Email address"));

        assertThat(auditRepository.count()).isGreaterThan(before);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void disablingAiGatesThePayload() throws Exception {
        mockMvc.perform(put("/api/v1/privacy/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(false));

        mockMvc.perform(post("/api/v1/privacy/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"SELECT 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacyStatus").value("AI_DISABLED"))
                .andExpect(jsonPath("$.validation.aiEnabled").value(false));

        // restore for other tests sharing the context
        mockMvc.perform(put("/api/v1/privacy/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiEnabled\":true}"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void defaultSettingsAreSafe() throws Exception {
        mockMvc.perform(get("/api/v1/privacy/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sqlSanitizationEnabled").value(true));
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/privacy/settings"))
                .andExpect(status().isUnauthorized());
    }
}
