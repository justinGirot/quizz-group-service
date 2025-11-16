package com.quizz.group.util;

import com.quizz.group.dto.GroupDTO;
import com.quizz.group.dto.InvitationDTO;
import com.quizz.group.dto.MemberDTO;
import com.quizz.group.model.Group;
import com.quizz.group.model.GroupInvitation;
import com.quizz.group.model.GroupMember;

/**
 * Utility class for mapping entities to DTOs.
 */
public class MapperUtil {

    private MapperUtil() {
        // Utility class
    }

    /**
     * Map Group entity to GroupDTO.
     */
    public static GroupDTO toGroupDTO(Group group) {
        return toGroupDTO(group, null);
    }

    /**
     * Map Group entity to GroupDTO with group admin name.
     */
    public static GroupDTO toGroupDTO(Group group, String groupAdminName) {
        return GroupDTO.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .type(group.getType())
                .createdBy(group.getCreatedBy())
                .groupAdminId(group.getGroupAdminId())
                .groupAdminName(groupAdminName)
                .avatarUrl(group.getAvatarUrl())
                .memberCount(group.getMemberCount())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    /**
     * Map GroupMember entity to MemberDTO.
     */
    public static MemberDTO toMemberDTO(GroupMember member) {
        return toMemberDTO(member, null, null);
    }

    /**
     * Map GroupMember entity to MemberDTO with user details.
     */
    public static MemberDTO toMemberDTO(GroupMember member, String userDisplayName, String userAvatarUrl) {
        return MemberDTO.builder()
                .id(member.getId())
                .groupId(member.getGroupId())
                .userId(member.getUserId())
                .userDisplayName(userDisplayName)
                .userAvatarUrl(userAvatarUrl)
                .role(member.getRole())
                .status(member.getStatus())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    /**
     * Map GroupInvitation entity to InvitationDTO.
     */
    public static InvitationDTO toInvitationDTO(GroupInvitation invitation) {
        return toInvitationDTO(invitation, null, null);
    }

    /**
     * Map GroupInvitation entity to InvitationDTO with denormalized data.
     */
    public static InvitationDTO toInvitationDTO(GroupInvitation invitation, String groupName, String invitedByName) {
        return InvitationDTO.builder()
                .id(invitation.getId())
                .groupId(invitation.getGroupId())
                .groupName(groupName)
                .invitedBy(invitation.getInvitedBy())
                .invitedByName(invitedByName)
                .inviteeEmail(invitation.getInviteeEmail())
                .inviteeUserId(invitation.getInviteeUserId())
                .status(invitation.getStatus())
                .token(invitation.getToken())
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}
