package com.dbperf.connection.service;

import com.dbperf.connection.domain.SslMode;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Opens short-lived, strictly read-only JDBC connections to monitored
 * target databases. Read-only is enforced in depth:
 * 1. session default_transaction_read_only=on via startup options
 * 2. Connection.setReadOnly(true)
 * Targets are user databases we must never mutate — no pooling, no reuse.
 */
@Component
public class TargetConnectionFactory {

    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int SOCKET_TIMEOUT_SECONDS = 15;

    public Connection open(String host, int port, String databaseName, String username,
                           String password, SslMode sslMode) throws SQLException {
        String url = "jdbc:postgresql://%s:%d/%s".formatted(host, port, databaseName);

        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("sslmode", sslMode.jdbcValue());
        props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
        props.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_SECONDS));
        props.setProperty("options", "-c default_transaction_read_only=on");
        props.setProperty("ApplicationName", "dbperf-ai-monitor");

        Connection connection = DriverManager.getConnection(url, props);
        connection.setReadOnly(true);
        return connection;
    }
}
