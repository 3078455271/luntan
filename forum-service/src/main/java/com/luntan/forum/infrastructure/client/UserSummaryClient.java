package com.luntan.forum.infrastructure.client;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class UserSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(UserSummaryClient.class);
    private static final String KEY_PREFIX = "forum:user-summary:";

    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final String internalToken;
    private final Duration cacheTtl;

    public UserSummaryClient(RestClient identityRestClient,
                             StringRedisTemplate redisTemplate,
                             @Value("${app.security.internal-token}") String internalToken,
                             @Value("${app.cache.user-summary-ttl}") Duration cacheTtl) {
        this.restClient = identityRestClient;
        this.redisTemplate = redisTemplate;
        this.internalToken = internalToken;
        this.cacheTtl = cacheTtl;
    }

    public Map<Long, UserSummary> findAll(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> orderedIds = userIds.stream().sorted().toList();
        List<String> keys = orderedIds.stream().map(id -> KEY_PREFIX + id).toList();
        List<String> cached = redisTemplate.opsForValue().multiGet(keys);
        Map<Long, UserSummary> result = new LinkedHashMap<>();
        for (int index = 0; index < orderedIds.size(); index++) {
            String value = cached == null ? null : cached.get(index);
            if (value != null) {
                result.put(orderedIds.get(index), decode(orderedIds.get(index), value));
            }
        }
        Set<Long> missing = orderedIds.stream().filter(id -> !result.containsKey(id)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            result.putAll(fetchMissing(missing));
        }
        missing.stream().filter(id -> !result.containsKey(id)).forEach(id -> result.put(id, fallback(id)));
        return Map.copyOf(result);
    }

    private Map<Long, UserSummary> fetchMissing(Set<Long> missing) {
        try {
            List<UserSummary> summaries = restClient.post()
                    .uri("/internal/users/summaries")
                    .header("X-Internal-Token", internalToken)
                    .body(Map.of("userIds", missing))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (summaries == null) {
                return Map.of();
            }
            summaries.forEach(summary -> redisTemplate.opsForValue().set(
                    KEY_PREFIX + summary.id(), encode(summary), cacheTtl));
            return summaries.stream().collect(Collectors.toMap(UserSummary::id, Function.identity()));
        } catch (RestClientException exception) {
            log.warn("Unable to load user summaries from identity service: {}", exception.getMessage());
            return missing.stream().collect(Collectors.toMap(Function.identity(), UserSummaryClient::fallback));
        }
    }

    private static String encode(UserSummary summary) {
        return String.join(".", encodePart(summary.username()), encodePart(summary.displayName()), encodePart(summary.avatarUrl()));
    }

    private static UserSummary decode(Long id, String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 3) {
            return fallback(id);
        }
        return new UserSummary(id, decodePart(parts[0]), decodePart(parts[1]), decodePart(parts[2]));
    }

    private static String encodePart(String value) {
        if (value == null) return "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        if (value.isEmpty()) return null;
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static UserSummary fallback(Long id) {
        return new UserSummary(id, "user-" + id, "用户 " + id, null);
    }

    public record UserSummary(Long id, String username, String displayName, String avatarUrl) {
    }

    @Configuration
    static class ClientConfiguration {
        @Bean
        RestClient identityRestClient(@Value("${app.services.identity-url}") String identityUrl) {
            return RestClient.builder().baseUrl(identityUrl).build();
        }
    }
}