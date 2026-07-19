package com.dbperf.secrets;

import com.dbperf.config.SecretsProperties;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Production credential store backed by Google Secret Manager.
 * Reference format: "gcp:projects/&lt;p&gt;/secrets/&lt;id&gt;/versions/&lt;n&gt;".
 * The Cloud Run service account needs roles/secretmanager.admin on the
 * project (or a narrower custom role with create/access/delete).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.secrets.provider", havingValue = "gcp")
public class GcpSecretManagerStore implements SecretStore {

    static final String PREFIX = "gcp:";

    private final String projectId;

    public GcpSecretManagerStore(SecretsProperties properties) {
        if (properties.gcpProjectId() == null || properties.gcpProjectId().isBlank()) {
            throw new IllegalStateException("app.secrets.gcp-project-id is required when provider=gcp");
        }
        this.projectId = properties.gcpProjectId();
        log.info("Secret store: Google Secret Manager (project {})", projectId);
    }

    @Override
    public String store(String name, String value) {
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            Secret secret = Secret.newBuilder()
                    .setReplication(Replication.newBuilder()
                            .setAutomatic(Replication.Automatic.newBuilder().build())
                            .build())
                    .build();
            Secret created = client.createSecret(ProjectName.of(projectId), name, secret);
            SecretVersion version = client.addSecretVersion(created.getName(), SecretPayload.newBuilder()
                    .setData(ByteString.copyFromUtf8(value))
                    .build());
            return PREFIX + version.getName();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store secret in Secret Manager", e);
        }
    }

    @Override
    public String retrieve(String reference) {
        if (!reference.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not a GCP secret reference");
        }
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            return client.accessSecretVersion(reference.substring(PREFIX.length()))
                    .getPayload().getData().toStringUtf8();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read secret from Secret Manager", e);
        }
    }

    @Override
    public void delete(String reference) {
        if (!reference.startsWith(PREFIX)) {
            return;
        }
        // versions/<n> -> parent secret resource
        String versionName = reference.substring(PREFIX.length());
        String secretName = versionName.replaceAll("/versions/.*$", "");
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            client.deleteSecret(secretName);
        } catch (Exception e) {
            // deletion is best-effort; the connection row is already gone
            log.warn("Failed to delete secret {}: {}", secretName, e.getMessage());
        }
    }
}
