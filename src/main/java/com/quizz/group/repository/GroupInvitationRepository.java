package com.quizz.group.repository;

import com.quizz.group.model.GroupInvitation;
import com.quizz.group.model.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for GroupInvitation entity.
 */
@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    /**
     * Find an invitation by token.
     */
    Optional<GroupInvitation> findByToken(String token);

    /**
     * Find all pending invitations for a group.
     */
    List<GroupInvitation> findByGroupIdAndStatus(Long groupId, InvitationStatus status);

    /**
     * Find pending invitations for a user by email.
     */
    List<GroupInvitation> findByInviteeEmailAndStatus(String email, InvitationStatus status);

    /**
     * Find pending invitations for a user by user ID.
     */
    List<GroupInvitation> findByInviteeUserIdAndStatus(Long userId, InvitationStatus status);

    /**
     * Check if an active invitation exists for a user in a group.
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM GroupInvitation i " +
           "WHERE i.groupId = :groupId AND " +
           "(i.inviteeEmail = :email OR i.inviteeUserId = :userId) AND " +
           "i.status = :status AND i.expiresAt > :now")
    boolean existsActiveInvitation(@Param("groupId") Long groupId,
                                   @Param("email") String email,
                                   @Param("userId") Long userId,
                                   @Param("status") InvitationStatus status,
                                   @Param("now") LocalDateTime now);

    /**
     * Find expired invitations that need to be updated.
     */
    @Query("SELECT i FROM GroupInvitation i WHERE i.status = :status AND i.expiresAt < :now")
    List<GroupInvitation> findExpiredInvitations(@Param("status") InvitationStatus status,
                                                  @Param("now") LocalDateTime now);

    /**
     * Delete all invitations for a group (for group deletion).
     */
    void deleteByGroupId(Long groupId);
}
