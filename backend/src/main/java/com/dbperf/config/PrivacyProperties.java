package com.dbperf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Privacy &amp; Sanitization Engine configuration.
 *
 * @param enabled           master switch for the sanitization pipeline (default true)
 * @param redactionToken    placeholder that replaces every masked value
 * @param blockOnResidualPii when true, a payload that still contains detectable
 *                          PII after sanitization is blocked rather than sent to the AI
 * @param auditEnabled      persist an audit record for every sanitization pass
 */
@ConfigurationProperties(prefix = "app.privacy")
public record PrivacyProperties(
        Boolean enabled,
        String redactionToken,
        Boolean blockOnResidualPii,
        Boolean auditEnabled) {

    public PrivacyProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (redactionToken == null || redactionToken.isBlank()) {
            redactionToken = "<REDACTED>";
        }
        if (blockOnResidualPii == null) {
            blockOnResidualPii = true;
        }
        if (auditEnabled == null) {
            auditEnabled = true;
        }
    }
}
