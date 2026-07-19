package com.dbperf.secrets;

/**
 * Abstraction over credential storage. Callers persist only the opaque
 * reference returned by {@link #store}; the plaintext never touches the
 * application database.
 */
public interface SecretStore {

    /**
     * @param name  stable identifier hint (used as the secret id on GCP)
     * @param value plaintext to protect
     * @return opaque reference resolvable by {@link #retrieve}
     */
    String store(String name, String value);

    String retrieve(String reference);

    void delete(String reference);
}
