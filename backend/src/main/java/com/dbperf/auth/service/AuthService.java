package com.dbperf.auth.service;

import com.dbperf.auth.dto.AuthResponse;
import com.dbperf.auth.dto.LoginRequest;
import com.dbperf.auth.dto.RegisterRequest;
import com.dbperf.auth.dto.UserResponse;
import com.dbperf.common.exception.DuplicateResourceException;
import com.dbperf.common.exception.ResourceNotFoundException;
import com.dbperf.security.JwtService;
import com.dbperf.user.domain.Role;
import com.dbperf.user.domain.User;
import com.dbperf.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .fullName(request.fullName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();
        // flush so @CreationTimestamp is populated before building the response
        user = userRepository.saveAndFlush(user);
        log.info("Registered new user {}", user.getId());

        return AuthResponse.bearer(jwtService.generateToken(user), jwtService.expiresInSeconds(),
                UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        log.info("User {} logged in", user.getId());

        return AuthResponse.bearer(jwtService.generateToken(user), jwtService.expiresInSeconds(),
                UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
