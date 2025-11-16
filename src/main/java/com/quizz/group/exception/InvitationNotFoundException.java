package com.quizz.group.exception;

/**
 * Exception thrown when an invitation is not found.
 */
public class InvitationNotFoundException extends RuntimeException {
    public InvitationNotFoundException(String message) {
        super(message);
    }

    public InvitationNotFoundException(String token, boolean ignored) {
        super("Invitation not found with token: " + token);
    }
}
