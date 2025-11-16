package com.quizz.group.model;

/**
 * Enum representing the status of a group invitation.
 */
public enum InvitationStatus {
    /**
     * Invitation awaiting response
     */
    PENDING,

    /**
     * Invitation was accepted
     */
    ACCEPTED,

    /**
     * Invitation was declined
     */
    DECLINED,

    /**
     * Invitation expired
     */
    EXPIRED
}
