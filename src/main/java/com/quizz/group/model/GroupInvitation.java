package com.quizz.group.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a group invitation.
 */
@Entity
@Table(name = "group_invitations", indexes = {
    @Index(name = "idx_invitations_token", columnList = "token"),
    @Index(name = "idx_invitations_email", columnList = "invitee_email"),
    @Index(name = "idx_invitations_user", columnList = "invitee_user_id"),
    @Index(name = "idx_invitations_group", columnList = "group_id, status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @Column(name = "invitee_email", length = 255)
    private String inviteeEmail;

    @Column(name = "invitee_user_id")
    private Long inviteeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
