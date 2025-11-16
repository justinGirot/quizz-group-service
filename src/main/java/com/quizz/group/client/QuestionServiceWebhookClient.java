package com.quizz.group.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Client for calling Question Service webhook endpoints to invalidate caches.
 * Failures are logged but do not cause the operation to fail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionServiceWebhookClient {

    private final RestTemplate restTemplate;

    @Value("${question.service.url}")
    private String questionServiceUrl;

    /**
     * Invalidate the user's admin groups cache in Question Service.
     * Called when a user's admin group memberships change.
     *
     * @param userId The user ID whose admin groups cache should be invalidated
     */
    public void invalidateUserAdminGroupsCache(Long userId) {
        try {
            String url = questionServiceUrl + "/api/cache/invalidate/user/" + userId + "/admin-groups";
            log.debug("Invalidating admin groups cache for userId={} at {}", userId, url);

            restTemplate.postForEntity(url, null, Void.class);

            log.debug("Successfully invalidated admin groups cache for userId={}", userId);
        } catch (Exception e) {
            // Log the error but don't fail the operation
            log.warn("Failed to invalidate admin groups cache for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Invalidate the user's groups cache in Question Service.
     * Called when a user's group memberships change.
     *
     * @param userId The user ID whose groups cache should be invalidated
     */
    public void invalidateUserGroupsCache(Long userId) {
        try {
            String url = questionServiceUrl + "/api/cache/invalidate/user/" + userId + "/groups";
            log.debug("Invalidating groups cache for userId={} at {}", userId, url);

            restTemplate.postForEntity(url, null, Void.class);

            log.debug("Successfully invalidated groups cache for userId={}", userId);
        } catch (Exception e) {
            // Log the error but don't fail the operation
            log.warn("Failed to invalidate groups cache for userId={}: {}", userId, e.getMessage());
        }
    }
}
