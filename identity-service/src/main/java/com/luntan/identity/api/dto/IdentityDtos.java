package com.luntan.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 64) String displayName) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String identifier,
            @NotBlank @Size(max = 72) String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 64) String displayName,
            @Size(max = 500) String bio,
            @Size(max = 512) String avatarUrl) {
    }

    public record AuthResponse(
            String tokenType,
            String accessToken,
            long expiresInSeconds,
            String refreshToken,
            UserView user) {
    }

    public record UserView(
            Long id,
            String username,
            String email,
            String displayName,
            String bio,
            String avatarUrl,
            String role,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record UserSummary(Long id, String username, String displayName, String avatarUrl) {
    }

    public record UserSummaryRequest(List<Long> userIds) {
        public UserSummaryRequest {
            userIds = userIds == null ? List.of() : List.copyOf(userIds);
        }
    }
}