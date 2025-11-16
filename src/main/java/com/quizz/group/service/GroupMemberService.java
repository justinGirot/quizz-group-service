package com.quizz.group.service;

import com.quizz.group.client.QuestionServiceWebhookClient;
import com.quizz.group.dto.MemberDTO;
import com.quizz.group.dto.UpdateMemberRoleRequest;
import com.quizz.group.exception.BadRequestException;
import com.quizz.group.exception.GroupNotFoundException;
import com.quizz.group.exception.UnauthorizedException;
import com.quizz.group.model.*;
import com.quizz.group.repository.GroupMemberRepository;
import com.quizz.group.repository.GroupRepository;
import com.quizz.group.util.MapperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing group memberships.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupMemberService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final QuestionServiceWebhookClient webhookClient;

    /**
     * Join a public group.
     */
    @Transactional
    public MemberDTO joinGroup(Long groupId, Long userId) {
        log.info("User {} joining group {}", userId, groupId);

        // Verify group exists and is public
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        if (group.getType() != GroupType.PUBLIC) {
            throw new BadRequestException("Cannot join private group without invitation");
        }

        // Check if already a member
        if (groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE)) {
            throw new BadRequestException("User is already a member of this group");
        }

        // Create membership
        GroupMember member = GroupMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();

        GroupMember savedMember = groupMemberRepository.save(member);

        // Increment member count
        groupRepository.incrementMemberCount(groupId);

        // Invalidate user's groups cache in Question Service
        webhookClient.invalidateUserGroupsCache(userId);

        log.info("User {} joined group {} successfully", userId, groupId);
        return MapperUtil.toMemberDTO(savedMember);
    }

    /**
     * Leave a group.
     */
    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        log.info("User {} leaving group {}", userId, groupId);

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BadRequestException("User is not a member of this group"));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BadRequestException("User is not an active member");
        }

        // Capture role before removal for webhook decision
        MemberRole roleBeforeRemoval = member.getRole();

        // Update status to removed
        member.setStatus(MemberStatus.REMOVED);
        member.setRemovedAt(LocalDateTime.now());
        groupMemberRepository.save(member);

        // Decrement member count
        groupRepository.decrementMemberCount(groupId);

        // Invalidate user's groups cache in Question Service
        webhookClient.invalidateUserGroupsCache(userId);
        // If user was an admin, also invalidate admin groups cache
        if (roleBeforeRemoval == MemberRole.ADMIN) {
            webhookClient.invalidateUserAdminGroupsCache(userId);
        }

        log.info("User {} left group {} successfully", userId, groupId);
    }

    /**
     * Remove a member from a group (by admin).
     */
    @Transactional
    public void removeMember(Long groupId, Long userId) {
        log.info("Removing user {} from group {}", userId, groupId);

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BadRequestException("User is not a member of this group"));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BadRequestException("User is not an active member");
        }

        // Capture role before removal for webhook decision
        MemberRole roleBeforeRemoval = member.getRole();

        // Update status to removed
        member.setStatus(MemberStatus.REMOVED);
        member.setRemovedAt(LocalDateTime.now());
        groupMemberRepository.save(member);

        // Decrement member count
        groupRepository.decrementMemberCount(groupId);

        // Invalidate user's groups cache in Question Service
        webhookClient.invalidateUserGroupsCache(userId);
        // If user was an admin, also invalidate admin groups cache
        if (roleBeforeRemoval == MemberRole.ADMIN) {
            webhookClient.invalidateUserAdminGroupsCache(userId);
        }

        log.info("User {} removed from group {} successfully", userId, groupId);
    }

    /**
     * Update member role.
     */
    @Transactional
    public MemberDTO updateMemberRole(Long groupId, Long userId, UpdateMemberRoleRequest request) {
        log.info("Updating role for user {} in group {} to {}", userId, groupId, request.getRole());

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BadRequestException("User is not a member of this group"));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BadRequestException("User is not an active member");
        }

        MemberRole oldRole = member.getRole();
        MemberRole newRole = request.getRole();

        member.setRole(newRole);
        GroupMember updatedMember = groupMemberRepository.save(member);

        // Invalidate user's groups cache in Question Service
        webhookClient.invalidateUserGroupsCache(userId);
        // If role changed to/from ADMIN, also invalidate admin groups cache
        if (oldRole == MemberRole.ADMIN || newRole == MemberRole.ADMIN) {
            webhookClient.invalidateUserAdminGroupsCache(userId);
        }

        log.info("Member role updated successfully");
        return MapperUtil.toMemberDTO(updatedMember);
    }

    /**
     * Get group members with pagination.
     */
    @Transactional(readOnly = true)
    public Page<MemberDTO> getGroupMembers(Long groupId, Pageable pageable) {
        log.debug("Getting members for group: {}", groupId);

        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }

        return groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE, pageable)
                .map(MapperUtil::toMemberDTO);
    }

    /**
     * Get user's groups.
     */
    @Transactional(readOnly = true)
    public List<MemberDTO> getUserGroups(Long userId) {
        log.debug("Getting groups for user: {}", userId);

        return groupMemberRepository.findByUserIdAndStatus(userId, MemberStatus.ACTIVE)
                .stream()
                .map(MapperUtil::toMemberDTO)
                .collect(Collectors.toList());
    }

    /**
     * Check if user is member of group.
     */
    @Transactional(readOnly = true)
    public boolean isMember(Long groupId, Long userId) {
        return groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE);
    }

    /**
     * Add member directly (for internal use, e.g., group creation or invitation acceptance).
     */
    @Transactional
    public void addMemberDirectly(Long groupId, Long userId, MemberRole role, MemberStatus status) {
        log.info("Adding user {} to group {} with role {} and status {}", userId, groupId, role, status);

        // Check if membership already exists
        if (groupMemberRepository.findByGroupIdAndUserId(groupId, userId).isPresent()) {
            log.warn("Membership already exists for user {} in group {}", userId, groupId);
            return;
        }

        GroupMember member = GroupMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role(role)
                .status(status)
                .build();

        groupMemberRepository.save(member);

        // Invalidate user's groups cache in Question Service only if status is ACTIVE
        if (status == MemberStatus.ACTIVE) {
            webhookClient.invalidateUserGroupsCache(userId);
            // If user is being added as an admin, also invalidate admin groups cache
            if (role == MemberRole.ADMIN) {
                webhookClient.invalidateUserAdminGroupsCache(userId);
            }
        }

        log.info("Member added successfully");
    }

    /**
     * Get member details (for service-to-service calls).
     */
    @Transactional(readOnly = true)
    public MemberDTO getMemberDetails(Long groupId, Long userId) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BadRequestException("User is not a member of this group"));

        return MapperUtil.toMemberDTO(member);
    }

    /**
     * Get all group IDs where user is an active member (any role).
     * Used for Question Service integration.
     */
    @Transactional(readOnly = true)
    public List<Long> getUserGroupIds(Long userId) {
        log.debug("Fetching all group IDs for userId={}", userId);
        return groupMemberRepository.findGroupIdsByUserId(userId);
    }

    /**
     * Get group IDs where user has ADMIN role.
     * Used for Question Service integration - determines which group questions user can manage.
     */
    @Transactional(readOnly = true)
    public List<Long> getUserAdminGroupIds(Long userId) {
        log.debug("Fetching admin group IDs for userId={}", userId);
        return groupMemberRepository.findGroupIdsByUserIdAndRole(userId, MemberRole.ADMIN);
    }
}
