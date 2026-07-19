package com.dbperf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider           "local" (AES-GCM, dev) or "gcp" (Google Secret Manager, prod)
 * @param localEncryptionKey passphrase for the local provider (min 16 chars)
 * @param gcpProjectId       GCP project hosting the secrets (gcp provider only)
 */
@ConfigurationProperties(prefix = "app.secrets")
public record SecretsProperties(String provider, String localEncryptionKey, String gcpProjectId) {
}
