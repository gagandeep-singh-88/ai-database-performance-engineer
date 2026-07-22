package com.dbperf.user.service;

import com.dbperf.auth.dto.UserResponse;
import com.dbperf.common.exception.InvalidRequestException;
import com.dbperf.user.domain.Role;
import com.dbperf.user.domain.User;
import com.dbperf.user.dto.ChangePasswordRequest;
import com.dbperf.user.dto.UpdateProfileRequest;
import com.dbperf.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ProfileService profileService;

    private User existingUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .passwordHash("$2a$10$current-hash")
                .fullName("Jane Doe")
                .organization("Acme")
                .role(Role.USER)
                .build();
    }

    @Test
    void updateProfileTrimsAndSavesNameAndOrganization() {
        User user = existingUser();
        when(currentUserService.require()).thenReturn(user);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = profileService.updateProfile(
                new UpdateProfileRequest("  Jane Smith  ", "  New Org  "));

        assertThat(response.fullName()).isEqualTo("Jane Smith");
        assertThat(response.organization()).isEqualTo("New Org");
        assertThat(response.email()).isEqualTo("jane@example.com");
    }

    @Test
    void updateProfileClearsBlankOrganization() {
        User user = existingUser();
        when(currentUserService.require()).thenReturn(user);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = profileService.updateProfile(new UpdateProfileRequest("Jane Doe", "   "));

        assertThat(response.organization()).isNull();
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        User user = existingUser();
        when(currentUserService.require()).thenReturn(user);
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> profileService.changePassword(
                new ChangePasswordRequest("wrong", "new-password-123")))
                .isInstanceOf(InvalidRequestException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordEncodesAndSavesNewPassword() {
        User user = existingUser();
        when(currentUserService.require()).thenReturn(user);
        when(passwordEncoder.matches("current-password", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("new-password-123")).thenReturn("$2a$10$new-hash");

        profileService.changePassword(new ChangePasswordRequest("current-password", "new-password-123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$new-hash");
    }
}
