package com.luntan.identity.application;

import com.luntan.identity.api.dto.IdentityDtos;
import com.luntan.identity.domain.ApiException;
import com.luntan.identity.infrastructure.persistence.UserEntity;
import com.luntan.identity.infrastructure.persistence.UserRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public IdentityDtos.UserView getProfile(Long userId) {
        return toView(requireUser(userId));
    }

    @Transactional
    public IdentityDtos.UserView updateProfile(Long userId, IdentityDtos.UpdateProfileRequest request) {
        UserEntity user = requireUser(userId);
        user.updateProfile(
                request.displayName().trim(),
                blankToNull(request.bio()),
                blankToNull(request.avatarUrl()));
        return toView(user);
    }

    @Transactional(readOnly = true)
    public List<IdentityDtos.UserSummary> findSummaries(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllByIdIn(userIds).stream()
                .filter(user -> user.getStatus() == UserEntity.Status.ACTIVE)
                .map(UserService::toSummary)
                .toList();
    }

    private UserEntity requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
    }

    public static IdentityDtos.UserView toView(UserEntity user) {
        return new IdentityDtos.UserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private static IdentityDtos.UserSummary toSummary(UserEntity user) {
        return new IdentityDtos.UserSummary(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}