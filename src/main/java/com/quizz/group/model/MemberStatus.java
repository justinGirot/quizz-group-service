package com.quizz.group.model;

/**
 * Enum representing the status of a group membership.
 */
public enum MemberStatus {
    /**
     * Currently active member
     */
    ACTIVE,

    /**
     * Invitation pending acceptance
     */
    PENDING,

    /**
     * Previously a member, now removed
     */
    REMOVED
}
