package com.quizz.group.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for JWT.
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {

    /**
     * Secret key for JWT signing
     */
    private String secret;

    /**
     * JWT expiration time in milliseconds
     */
    private Long expiration;
}
