package com.quizz.group.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a group membership.
 */
@Entity
@Table(name = "group_members",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_user", columnNames = {"group_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_group_members_user", columnList = "user_id, status"),
        @Index(name = "idx_group_members_group", columnList = "group_id, status")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    @Column(name = "removed_at")
    private LocalDateTime removedAt;
}
