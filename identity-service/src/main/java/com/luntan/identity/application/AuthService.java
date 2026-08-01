package com.luntan.identity.application;

import com.luntan.identity.api.dto.IdentityDtos;
import com.luntan.identity.domain.ApiException;
import com.luntan.identity.infrastructure.persistence.RefreshSessionEntity;
import com.luntan.identity.infrastructure.persistence.RefreshSessionRepository;
import com.luntan.identity.infrastructure.persistence.UserEntity;
import com.luntan.identity.infrastructure.persistence.UserRepository;
import com.luntan.identity.infrastructure.security.AccessTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

    private final UserRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       RefreshSessionRepository refreshSessionRepository,
                       PasswordEncoder passwordEncoder,
                       AccessTokenService accessTokenService,
                       StringRedisTemplate redisTemplate,
                       @Value("${app.security.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.userRepository = userRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.redisTemplate = redisTemplate;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public IdentityDtos.AuthResponse register(IdentityDtos.RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已被使用");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "邮箱已被使用");
        }
        UserEntity user = userRepository.save(new UserEntity(
                username,
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim()));
        return issueTokenPair(user);
    }

    @Transactional
    public IdentityDtos.AuthResponse login(IdentityDtos.LoginRequest request) {
        String identifier = request.identifier().trim();
        UserEntity user = identifier.contains("@")
                ? userRepository.findByEmailIgnoreCase(identifier).orElseThrow(this::invalidCredentials)
                : userRepository.findByUsernameIgnoreCase(identifier).orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        ensureActive(user);
        return issueTokenPair(user);
    }

    @Transactional
    public IdentityDtos.AuthResponse refresh(String refreshToken) {
        String tokenHash = hash(refreshToken);
        String cachedUserId = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + tokenHash);
        RefreshSessionEntity session = refreshSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidRefreshToken);
        Instant now = Instant.now();
        if (!session.isActiveAt(now)) {
            throw invalidRefreshToken();
        }
        UserEntity user = session.getUser();
        if (cachedUserId != null && !cachedUserId.equals(user.getId().toString())) {
            throw invalidRefreshToken();
        }
        if (cachedUserId == null) {
            redisTemplate.opsForValue().set(
                    REFRESH_KEY_PREFIX + tokenHash,
                    user.getId().toString(),
                    Duration.between(now, session.getExpiresAt()));
        }
        ensureActive(user);
        session.revoke(now);
        redisTemplate.delete(REFRESH_KEY_PREFIX + tokenHash);
        return issueTokenPair(user);
    }

    @Transactional
    public void logout(Long currentUserId, String refreshToken) {
        String tokenHash = hash(refreshToken);
        refreshSessionRepository.findByTokenHash(tokenHash).ifPresent(session -> {
            if (!session.getUser().getId().equals(currentUserId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "SESSION_OWNER_MISMATCH", "不能注销其他用户的会话");
            }
            session.revoke(Instant.now());
        });
        redisTemplate.delete(REFRESH_KEY_PREFIX + tokenHash);
    }

    private IdentityDtos.AuthResponse issueTokenPair(UserEntity user) {
        AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(user);
        byte[] tokenBytes = new byte[48];
        secureRandom.nextBytes(tokenBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = hash(refreshToken);
        Instant expiresAt = Instant.now().plus(refreshTokenTtl);
        refreshSessionRepository.save(new RefreshSessionEntity(user, tokenHash, expiresAt));
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + tokenHash,
                user.getId().toString(),
                refreshTokenTtl);
        return new IdentityDtos.AuthResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresInSeconds(),
                refreshToken,
                UserService.toView(user));
    }

    private void ensureActive(UserEntity user) {
        if (user.getStatus() != UserEntity.Status.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_DISABLED", "账号已停用");
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名、邮箱或密码错误");
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "刷新令牌无效或已过期");
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}