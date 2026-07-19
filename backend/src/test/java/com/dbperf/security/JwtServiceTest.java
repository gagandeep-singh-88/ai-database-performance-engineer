package com.dbperf.security;

import com.dbperf.config.JwtProperties;
import com.dbperf.user.domain.Role;
import com.dbperf.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties("test-only-jwt-secret-0123456789abcdefghij", 60));
        user = User.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .passwordHash("hash")
                .fullName("Jane Doe")
                .role(Role.USER)
                .build();
    }

    @Test
    void generatedTokenContainsSubject() {
        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractSubject(token)).contains("jane@example.com");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 4) + "xxxx";

        assertThat(jwtService.extractSubject(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtService other = new JwtService(new JwtProperties("another-secret-key-9876543210zyxwvutsrq", 60));
        String foreignToken = other.generateToken(user);

        assertThat(jwtService.extractSubject(foreignToken)).isEmpty();
    }

    @Test
    void garbageInputIsRejectedWithoutThrowing() {
        assertThat(jwtService.extractSubject("not-a-jwt")).isEmpty();
        assertThat(jwtService.extractSubject("")).isEmpty();
    }
}
