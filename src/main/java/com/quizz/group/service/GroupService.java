package com.quizz.group.service;

import com.quizz.group.dto.CreateGroupRequest;
import com.quizz.group.dto.GroupDTO;
import com.quizz.group.dto.UpdateGroupAdminRequest;
import com.quizz.group.dto.UpdateGroupRequest;
import com.quizz.group.exception.GroupAlreadyExistsException;
import com.quizz.group.exception.GroupNotFoundException;
import com.quizz.group.model.Group;
import com.quizz.group.model.GroupType;
import com.quizz.group.model.MemberRole;
import com.quizz.group.model.MemberStatus;
import com.quizz.group.repository.GroupRepository;
import com.quizz.group.util.MapperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing groups.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberService groupMemberService;

    /**
     * Create a new group.
     * Only application admins can create groups.
     */
    @Transactional
    public GroupDTO createGroup(CreateGroupRequest request, Long createdBy) {
        log.info("Creating group: {} by user: {}", request.getName(), createdBy);

        // Check if group name already exists
        if (groupRepository.existsByName(request.getName())) {
            throw new GroupAlreadyExistsException(request.getName(), true);
        }

        // Create group entity
        Group group = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .createdBy(createdBy)
                .groupAdminId(request.getGroupAdminId())
                .avatarUrl(request.getAvatarUrl())
                .memberCount(1) // Group admin is first member
                .build();

        Group savedGroup = groupRepository.save(group);

        // Add group admin as first member
        groupMemberService.addMemberDirectly(
                savedGroup.getId(),
                request.getGroupAdminId(),
                MemberRole.ADMIN,
                MemberStatus.ACTIVE
        );

        log.info("Group created successfully with ID: {}", savedGroup.getId());
        return MapperUtil.toGroupDTO(savedGroup);
    }

    /**
     * Update group details.
     */
    @Transactional
    public GroupDTO updateGroup(Long groupId, UpdateGroupRequest request) {
        log.info("Updating group: {}", groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        // Update fields if provided
        if (request.getName() != null && !request.getName().equals(group.getName())) {
            if (groupRepository.existsByName(request.getName())) {
                throw new GroupAlreadyExistsException(request.getName(), true);
            }
            group.setName(request.getName());
        }

        if (request.getDescription() != null) {
            group.setDescription(request.getDescription());
        }

        if (request.getType() != null) {
            group.setType(request.getType());
        }

        if (request.getAvatarUrl() != null) {
            group.setAvatarUrl(request.getAvatarUrl());
        }

        Group updatedGroup = groupRepository.save(group);
        log.info("Group updated successfully: {}", groupId);

        return MapperUtil.toGroupDTO(updatedGroup);
    }

    /**
     * Update group admin.
     */
    @Transactional
    public GroupDTO updateGroupAdmin(Long groupId, UpdateGroupAdminRequest request) {
        log.info("Updating group admin for group: {} to user: {}", groupId, request.getGroupAdminId());

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        // Update group admin
        group.setGroupAdminId(request.getGroupAdminId());
        Group updatedGroup = groupRepository.save(group);

        // Ensure new admin is a member
        if (!groupMemberService.isMember(groupId, request.getGroupAdminId())) {
            groupMemberService.addMemberDirectly(
                    groupId,
                    request.getGroupAdminId(),
                    MemberRole.ADMIN,
                    MemberStatus.ACTIVE
            );
            groupRepository.incrementMemberCount(groupId);
        }

        log.info("Group admin updated successfully");
        return MapperUtil.toGroupDTO(updatedGroup);
    }

    /**
     * Delete a group.
     */
    @Transactional
    public void deleteGroup(Long groupId) {
        log.info("Deleting group: {}", groupId);

        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }

        // Cascade delete will handle members and invitations
        groupRepository.deleteById(groupId);
        log.info("Group deleted successfully: {}", groupId);
    }

    /**
     * Get group by ID.
     */
    @Transactional(readOnly = true)
    public GroupDTO getGroupById(Long groupId) {
        log.debug("Getting group by ID: {}", groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        return MapperUtil.toGroupDTO(group);
    }

    /**
     * Get all public groups with pagination.
     */
    @Transactional(readOnly = true)
    public Page<GroupDTO> getPublicGroups(Pageable pageable) {
        log.debug("Getting public groups");

        return groupRepository.findByType(GroupType.PUBLIC, pageable)
                .map(MapperUtil::toGroupDTO);
    }

    /**
     * Search groups by name.
     */
    @Transactional(readOnly = true)
    public Page<GroupDTO> searchGroups(String search, Pageable pageable) {
        log.debug("Searching groups with term: {}", search);

        if (search == null || search.trim().isEmpty()) {
            return groupRepository.findAll(pageable)
                    .map(MapperUtil::toGroupDTO);
        }

        return groupRepository.searchByName(search, pageable)
                .map(MapperUtil::toGroupDTO);
    }

    /**
     * Check if user is group admin.
     */
    @Transactional(readOnly = true)
    public boolean isGroupAdmin(Long groupId, Long userId) {
        return groupRepository.findById(groupId)
                .map(group -> group.getGroupAdminId().equals(userId))
                .orElse(false);
    }

    /**
     * Get group entity (for internal use).
     */
    @Transactional(readOnly = true)
    public Group getGroupEntity(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
    }
}
