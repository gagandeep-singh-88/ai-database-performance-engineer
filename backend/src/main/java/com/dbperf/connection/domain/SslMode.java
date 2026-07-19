package com.dbperf.connection.domain;

public enum SslMode {
    DISABLE,
    PREFER,
    REQUIRE;

    /** Value expected by the PostgreSQL JDBC driver's sslmode parameter. */
    public String jdbcValue() {
        return name().toLowerCase();
    }
}
