package com.quizz.group.repository;

import com.quizz.group.model.GroupMember;
import com.quizz.group.model.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for GroupMember entity.
 */
@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    /**
     * Find a member by group ID and user ID.
     */
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * Find all members of a group with pagination.
     */
    Page<GroupMember> findByGroupIdAndStatus(Long groupId, MemberStatus status, Pageable pageable);

    /**
     * Find all groups a user is a member of.
     */
    List<GroupMember> findByUserIdAndStatus(Long userId, MemberStatus status);

    /**
     * Check if a user is a member of a group.
     */
    boolean existsByGroupIdAndUserIdAndStatus(Long groupId, Long userId, MemberStatus status);

    /**
     * Count active members in a group.
     */
    long countByGroupIdAndStatus(Long groupId, MemberStatus status);

    /**
     * Delete all members of a group (for group deletion).
     */
    void deleteByGroupId(Long groupId);
}
