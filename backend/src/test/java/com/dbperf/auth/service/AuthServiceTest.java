package com.dbperf.auth.service;

import com.dbperf.auth.dto.AuthResponse;
import com.dbperf.auth.dto.LoginRequest;
import com.dbperf.auth.dto.RegisterRequest;
import com.dbperf.common.exception.DuplicateResourceException;
import com.dbperf.security.JwtService;
import com.dbperf.user.domain.Role;
import com.dbperf.user.domain.User;
import com.dbperf.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User persistedUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .passwordHash("$2a$10$hash")
                .fullName("Jane Doe")
                .role(Role.USER)
                .build();
    }

    @Test
    void registerCreatesUserWithEncodedPasswordAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("Jane Doe", "Jane@Example.com", "secret-password");
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret-password")).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persistedUser());
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");
        when(jwtService.expiresInSeconds()).thenReturn(3600L);

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("jane@example.com");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Jane Doe", "jane@example.com", "secret-password")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginAuthenticatesAndReturnsToken() {
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(persistedUser()));
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");
        when(jwtService.expiresInSeconds()).thenReturn(3600L);

        AuthResponse response = authService.login(new LoginRequest("jane@example.com", "secret-password"));

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertThat(response.accessToken()).isEqualTo("jwt-token");
    }

    @Test
    void loginPropagatesBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
