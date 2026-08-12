package com.example.translator.auth;

import com.example.translator.user.AppRole;
import com.example.translator.user.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 100) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {
    }

    public record UserSummary(Long id, String email, String displayName, AppRole appRole, boolean enabled, Instant createdAt) {
        public static UserSummary from(AppUser user) {
            return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getAppRole(),
                    user.isEnabled(), user.getCreatedAt());
        }
    }

    public record AuthResponse(String token, UserSummary user) {
    }
}
