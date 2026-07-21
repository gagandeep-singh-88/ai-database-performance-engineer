package com.dbperf.privacy.domain;

/**
 * How the Privacy &amp; Sanitization Engine reacts to detected PII before a
 * payload reaches the AI provider.
 */
public enum SanitizationMode {
    /** Redact automatically and send the sanitized payload. */
    AUTOMATIC,
    /** Same enforcement as {@link #AUTOMATIC}; surfaced as a warning in the preview UI. */
    WARN_BEFORE_SENDING,
    /** Reject the request outright if the raw input contains any detectable PII. */
    STRICT_BLOCK
}
