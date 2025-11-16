package com.quizz.group.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a group in the system.
 */
@Entity
@Table(name = "groups", indexes = {
    @Index(name = "idx_groups_type", columnList = "type"),
    @Index(name = "idx_groups_created_by", columnList = "created_by"),
    @Index(name = "idx_groups_group_admin", columnList = "group_admin_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupType type;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "group_admin_id", nullable = false)
    private Long groupAdminId;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "member_count", nullable = false)
    @Builder.Default
    private Integer memberCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
