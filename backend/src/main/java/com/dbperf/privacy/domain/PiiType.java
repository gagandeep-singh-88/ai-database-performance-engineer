package com.dbperf.privacy.domain;

/**
 * Categories of sensitive data the privacy engine detects and redacts.
 * The label is human-readable (surfaced in the payload preview and audit
 * log); the enum name is the stable machine key persisted in audit records.
 * Raw matched values are NEVER stored anywhere — only these types + counts.
 */
public enum PiiType {

    EMAIL("Email address"),
    PHONE("Phone number"),
    CREDIT_CARD("Credit card number"),
    AADHAAR("Aadhaar number"),
    PAN("PAN number"),
    NATIONAL_ID("National identifier"),
    UUID("UUID"),
    JWT("JSON Web Token"),
    BEARER_TOKEN("Bearer / access token"),
    API_KEY("API key"),
    SECRET("Secret / password"),
    IP_ADDRESS("IP address"),
    CONNECTION_STRING("Database connection string"),
    LONG_NUMERIC_ID("Long numeric identifier"),
    STRING_LITERAL("Quoted string literal");

    private final String label;

    PiiType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
