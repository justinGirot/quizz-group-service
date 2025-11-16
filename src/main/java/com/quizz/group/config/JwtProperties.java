package com.quizz.group.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for JWT token management.
 * Binds properties from application.yml with prefix 'jwt'
 * Matches the configuration used by the auth-service.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Secret key used for signing JWT tokens.
     * Should be at least 256 bits (32 bytes) for HS256 algorithm.
     * Configure via environment variable in production.
     */
    @NotBlank(message = "JWT secret must not be blank")
    private String secret;

    /**
     * Token expiration time in milliseconds.
     * Default: 86400000 (24 hours)
     */
    @Positive(message = "JWT expiration must be positive")
    private long expiration = 86400000L;

    /**
     * Token issuer identifier
     */
    private String issuer = "quiz-auth-service";

    /**
     * Token audience (intended recipient)
     */
    private String audience = "quiz-application";

    /**
     * Cookie configuration for httpOnly cookie-based authentication
     */
    private CookieProperties cookie = new CookieProperties();

    /**
     * Nested configuration properties for JWT cookie settings.
     */
    @Data
    public static class CookieProperties {

        /**
         * Cookie name for storing JWT token
         */
        @NotBlank(message = "Cookie name must not be blank")
        private String name = "token";

        /**
         * Cookie max age in seconds (7 days by default)
         */
        @Positive(message = "Cookie max-age must be positive")
        private int maxAge = 604800; // 7 days

        /**
         * Whether cookie should be httpOnly (prevents JavaScript access)
         */
        private boolean httpOnly = true;

        /**
         * Whether cookie should be secure (HTTPS only)
         * Should be true in production
         */
        private boolean secure = false;

        /**
         * SameSite attribute for CSRF protection
         * Values: Strict, Lax, or None
         */
        @NotBlank(message = "SameSite attribute must not be blank")
        private String sameSite = "Strict";

        /**
         * Cookie path
         */
        @NotBlank(message = "Cookie path must not be blank")
        private String path = "/";
    }
}
