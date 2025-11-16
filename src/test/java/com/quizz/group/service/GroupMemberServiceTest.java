package com.quizz.group.service;

import com.quizz.group.dto.MemberDTO;
import com.quizz.group.dto.UpdateMemberRoleRequest;
import com.quizz.group.exception.BadRequestException;
import com.quizz.group.exception.GroupNotFoundException;
import com.quizz.group.model.*;
import com.quizz.group.repository.GroupMemberRepository;
import com.quizz.group.repository.GroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupMemberServiceTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private GroupMemberService groupMemberService;

    @Test
    @DisplayName("Should join public group successfully")
    void shouldJoinPublicGroupSuccessfully() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        Group group = Group.builder()
                .id(groupId)
                .name("Public Group")
                .type(GroupType.PUBLIC)
                .build();

        GroupMember savedMember = GroupMember.builder()
                .id(1L)
                .groupId(groupId)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE))
                .thenReturn(false);
        when(groupMemberRepository.save(any(GroupMember.class))).thenReturn(savedMember);

        // When
        MemberDTO result = groupMemberService.joinGroup(groupId, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getGroupId()).isEqualTo(groupId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(result.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        verify(groupRepository).incrementMemberCount(groupId);
    }

    @Test
    @DisplayName("Should throw exception when joining private group without invitation")
    void shouldThrowExceptionWhenJoiningPrivateGroup() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        Group group = Group.builder()
                .id(groupId)
                .name("Private Group")
                .type(GroupType.PRIVATE)
                .build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // When & Then
        assertThatThrownBy(() -> groupMemberService.joinGroup(groupId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot join private group");

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when user is already a member")
    void shouldThrowExceptionWhenAlreadyMember() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        Group group = Group.builder()
                .id(groupId)
                .type(GroupType.PUBLIC)
                .build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE))
                .thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> groupMemberService.joinGroup(groupId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already a member");

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should leave group successfully")
    void shouldLeaveGroupSuccessfully() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        GroupMember member = GroupMember.builder()
                .id(1L)
                .groupId(groupId)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();

        when(groupMemberRepository.findByGroupIdAndUserId(groupId, userId))
                .thenReturn(Optional.of(member));

        // When
        groupMemberService.leaveGroup(groupId, userId);

        // Then
        assertThat(member.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(member.getRemovedAt()).isNotNull();
        verify(groupMemberRepository).save(member);
        verify(groupRepository).decrementMemberCount(groupId);
    }

    @Test
    @DisplayName("Should throw exception when leaving group user is not member of")
    void shouldThrowExceptionWhenLeavingNonMemberGroup() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        when(groupMemberRepository.findByGroupIdAndUserId(groupId, userId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> groupMemberService.leaveGroup(groupId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a member");

        verify(groupRepository, never()).decrementMemberCount(any());
    }

    @Test
    @DisplayName("Should remove member successfully")
    void shouldRemoveMemberSuccessfully() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        GroupMember member = GroupMember.builder()
                .id(1L)
                .groupId(groupId)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();

        when(groupMemberRepository.findByGroupIdAndUserId(groupId, userId))
                .thenReturn(Optional.of(member));

        // When
        groupMemberService.removeMember(groupId, userId);

        // Then
        assertThat(member.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(member.getRemovedAt()).isNotNull();
        verify(groupMemberRepository).save(member);
        verify(groupRepository).decrementMemberCount(groupId);
    }

    @Test
    @DisplayName("Should update member role successfully")
    void shouldUpdateMemberRoleSuccessfully() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        UpdateMemberRoleRequest request = UpdateMemberRoleRequest.builder()
                .role(MemberRole.ADMIN)
                .build();

        GroupMember member = GroupMember.builder()
                .id(1L)
                .groupId(groupId)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();

        when(groupMemberRepository.findByGroupIdAndUserId(groupId, userId))
                .thenReturn(Optional.of(member));
        when(groupMemberRepository.save(any(GroupMember.class))).thenReturn(member);

        // When
        MemberDTO result = groupMemberService.updateMemberRole(groupId, userId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
        verify(groupMemberRepository).save(member);
    }

    @Test
    @DisplayName("Should throw exception when updating role of non-active member")
    void shouldThrowExceptionWhenUpdatingNonActiveMemberRole() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        UpdateMemberRoleRequest request = UpdateMemberRoleRequest.builder()
                .role(MemberRole.ADMIN)
                .build();

        GroupMember member = GroupMember.builder()
                .id(1L)
                .groupId(groupId)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.REMOVED)
                .build();

        when(groupMemberRepository.findByGroupIdAndUserId(groupId, userId))
                .thenReturn(Optional.of(member));

        // When & Then
        assertThatThrownBy(() -> groupMemberService.updateMemberRole(groupId, userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not an active member");

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should check if user is member")
    void shouldCheckIfUserIsMember() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE))
                .thenReturn(true);

        // When
        boolean result = groupMemberService.isMember(groupId, userId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when user is not member")
    void shouldReturnFalseWhenUserIsNotMember() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE))
                .thenReturn(false);

        // When
        boolean result = groupMemberService.isMember(groupId, userId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should add member directly")
    void shouldAddMemberDirectly() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        when(groupMemberRepository.findByGroupIdAndUserId(groupId, userId))
                .thenReturn(Optional.empty());

        // When
        groupMemberService.addMemberDirectly(groupId, userId, MemberRole.ADMIN, MemberStatus.ACTIVE);

        // Then
        verify(groupMemberRepository).save(any(GroupMember.class));
    }

    @Test
    @DisplayName("Should not add member directly if membership already exists")
    void shouldNotAddMemberDirectlyIfExists() {
        // Given
        Long groupId = 1L;
        Long userId = 5L;

        GroupMember existingMember = GroupMember.builder()
                .id(1L)
                .groupId(groupId)
                .userId(userId)
                .build();

        when(groupMemberRepository.findByGroupIdAndUserId(groupId, userId))
                .thenReturn(Optional.of(existingMember));

        // When
        groupMemberService.addMemberDirectly(groupId, userId, MemberRole.ADMIN, MemberStatus.ACTIVE);

        // Then
        verify(groupMemberRepository, never()).save(any());
    }
}
