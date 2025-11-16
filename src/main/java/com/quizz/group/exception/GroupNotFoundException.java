package com.quizz.group.exception;

/**
 * Exception thrown when a group is not found.
 */
public class GroupNotFoundException extends RuntimeException {
    public GroupNotFoundException(String message) {
        super(message);
    }

    public GroupNotFoundException(Long groupId) {
        super("Group not found with ID: " + groupId);
    }
}
