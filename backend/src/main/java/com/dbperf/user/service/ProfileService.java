package com.dbperf.user.service;

import com.dbperf.auth.dto.UserResponse;
import com.dbperf.common.exception.InvalidRequestException;
import com.dbperf.user.domain.User;
import com.dbperf.user.dto.ChangePasswordRequest;
import com.dbperf.user.dto.UpdateProfileRequest;
import com.dbperf.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile self-service for the Settings page: view/update name &amp; organization,
 * change password. Users can only ever act on their own account, resolved via
 * {@link CurrentUserService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public UserResponse current() {
        return UserResponse.from(currentUserService.require());
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User user = currentUserService.require();
        user.setFullName(request.fullName().trim());
        user.setOrganization(request.organization() == null || request.organization().isBlank()
                ? null : request.organization().trim());
        user = userRepository.save(user);
        log.info("User {} updated their profile", user.getId());
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUserService.require();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("User {} changed their password", user.getId());
    }
}
