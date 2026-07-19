package com.dbperf.secrets;

import com.dbperf.config.SecretsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalEncryptedSecretStoreTest {

    private LocalEncryptedSecretStore store;

    @BeforeEach
    void setUp() {
        store = new LocalEncryptedSecretStore(
                new SecretsProperties("local", "test-encryption-key-0123456789", null));
    }

    @Test
    void roundTripsSecrets() {
        String reference = store.store("dbconn-1", "s3cret-p@ssword");

        assertThat(reference).startsWith("local:").doesNotContain("s3cret-p@ssword");
        assertThat(store.retrieve(reference)).isEqualTo("s3cret-p@ssword");
    }

    @Test
    void producesDifferentCiphertextPerCall() {
        // random IV means identical plaintexts must not produce identical references
        assertThat(store.store("a", "same-value")).isNotEqualTo(store.store("b", "same-value"));
    }

    @Test
    void rejectsTamperedCiphertext() {
        String reference = store.store("dbconn-1", "value");
        String tampered = reference.substring(0, reference.length() - 5) + "AAAA=";

        assertThatThrownBy(() -> store.retrieve(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWrongKey() {
        String reference = store.store("dbconn-1", "value");
        LocalEncryptedSecretStore otherStore = new LocalEncryptedSecretStore(
                new SecretsProperties("local", "different-key-9876543210zyxwv", null));

        assertThatThrownBy(() -> otherStore.retrieve(reference)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsTooShortKey() {
        assertThatThrownBy(() -> new LocalEncryptedSecretStore(new SecretsProperties("local", "short", null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
