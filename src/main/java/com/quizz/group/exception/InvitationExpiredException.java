package com.quizz.group.exception;

/**
 * Exception thrown when an invitation has expired.
 */
public class InvitationExpiredException extends RuntimeException {
    public InvitationExpiredException(String message) {
        super(message);
    }
}
