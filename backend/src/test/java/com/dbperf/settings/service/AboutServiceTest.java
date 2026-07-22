package com.dbperf.settings.service;

import com.dbperf.config.AiProperties;
import com.dbperf.settings.dto.AboutResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AboutServiceTest {

    private AboutService service(AiProperties aiProperties) {
        AboutService service = new AboutService(aiProperties);
        ReflectionTestUtils.setField(service, "appName", "dbperf-backend");
        ReflectionTestUtils.setField(service, "appVersion", "0.1.0");
        ReflectionTestUtils.setField(service, "buildVersion", "test-build");
        return service;
    }

    @Test
    void reportsAnthropicWhenConfigured() {
        AiProperties properties = new AiProperties("anthropic", "sk-ant-test", null,
                "claude-opus-4-8", null, null, null, 16000);

        AboutResponse response = service(properties).about();

        assertThat(response.appName()).isEqualTo("dbperf-backend");
        assertThat(response.appVersion()).isEqualTo("0.1.0");
        assertThat(response.buildVersion()).isEqualTo("test-build");
        assertThat(response.aiProvider()).isEqualTo("Claude (Anthropic)");
        assertThat(response.aiModel()).isEqualTo("claude-opus-4-8");
        assertThat(response.cloudPlatform()).isEqualTo("Google Cloud Run");
        assertThat(response.javaVersion()).isNotBlank();
        assertThat(response.springBootVersion()).isNotBlank();
        assertThat(response.links().documentation()).contains("github.com");
    }

    @Test
    void reportsNotConfiguredWhenNoProviderKeyed() {
        AiProperties properties = new AiProperties("auto", null, null, null, null, null, null, 16000);

        AboutResponse response = service(properties).about();

        assertThat(response.aiProvider()).isEqualTo("Not configured");
        assertThat(response.aiModel()).isEqualTo("—");
    }
}
