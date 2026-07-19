package com.dbperf.secrets;

import com.dbperf.config.SecretsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Dev/local fallback for Google Secret Manager: AES-256-GCM with a key
 * derived from a configured passphrase. The reference embeds IV +
 * ciphertext ("local:&lt;base64&gt;"), so no extra storage is needed and
 * the app database only ever sees ciphertext.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.secrets.provider", havingValue = "local", matchIfMissing = true)
public class LocalEncryptedSecretStore implements SecretStore {

    static final String PREFIX = "local:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public LocalEncryptedSecretStore(SecretsProperties properties) {
        if (properties.localEncryptionKey() == null || properties.localEncryptionKey().length() < 16) {
            throw new IllegalStateException("app.secrets.local-encryption-key must be at least 16 characters");
        }
        this.key = deriveKey(properties.localEncryptionKey());
        log.info("Secret store: local AES-GCM (use provider=gcp with Secret Manager in production)");
    }

    private static SecretKey deriveKey(String passphrase) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive encryption key", e);
        }
    }

    @Override
    public String store(String name, String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    @Override
    public String retrieve(String reference) {
        if (!reference.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not a local secret reference");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(reference.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, combined, 0, IV_LENGTH));
            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }

    @Override
    public void delete(String reference) {
        // ciphertext lives inside the reference itself; nothing to clean up
    }
}
