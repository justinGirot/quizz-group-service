package com.quizz.group.exception;

/**
 * Exception thrown when attempting to create a group with a name that already exists.
 */
public class GroupAlreadyExistsException extends RuntimeException {
    public GroupAlreadyExistsException(String message) {
        super(message);
    }

    public GroupAlreadyExistsException(String name, boolean ignored) {
        super("Group with name '" + name + "' already exists");
    }
}
