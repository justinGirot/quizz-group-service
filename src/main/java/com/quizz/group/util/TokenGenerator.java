package com.quizz.group.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for generating secure random tokens.
 */
public class TokenGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    private TokenGenerator() {
        // Utility class
    }

    /**
     * Generate a cryptographically secure random token.
     */
    public static String generateToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }
}
