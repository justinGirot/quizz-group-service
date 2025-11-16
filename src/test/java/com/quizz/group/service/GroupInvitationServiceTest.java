package com.quizz.group.service;

import com.quizz.group.dto.InvitationDTO;
import com.quizz.group.dto.InviteUserRequest;
import com.quizz.group.dto.MemberDTO;
import com.quizz.group.exception.BadRequestException;
import com.quizz.group.exception.GroupNotFoundException;
import com.quizz.group.exception.InvitationExpiredException;
import com.quizz.group.exception.InvitationNotFoundException;
import com.quizz.group.model.*;
import com.quizz.group.repository.GroupInvitationRepository;
import com.quizz.group.repository.GroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupInvitationServiceTest {

    @Mock
    private GroupInvitationRepository invitationRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberService groupMemberService;

    @InjectMocks
    private GroupInvitationService groupInvitationService;

    @Test
    @DisplayName("Should create invitation successfully")
    void shouldCreateInvitationSuccessfully() {
        // Given
        Long groupId = 1L;
        Long invitedBy = 2L;

        InviteUserRequest request = InviteUserRequest.builder()
                .email("newuser@example.com")
                .build();

        GroupInvitation savedInvitation = GroupInvitation.builder()
                .id(1L)
                .groupId(groupId)
                .invitedBy(invitedBy)
                .inviteeEmail("newuser@example.com")
                .status(InvitationStatus.PENDING)
                .token("test-token")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(groupRepository.existsById(groupId)).thenReturn(true);
        when(invitationRepository.existsActiveInvitation(any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(invitationRepository.findByToken(any())).thenReturn(Optional.empty());
        when(invitationRepository.save(any(GroupInvitation.class))).thenReturn(savedInvitation);

        // When
        InvitationDTO result = groupInvitationService.inviteUser(groupId, invitedBy, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getGroupId()).isEqualTo(groupId);
        assertThat(result.getInviteeEmail()).isEqualTo("newuser@example.com");
        assertThat(result.getStatus()).isEqualTo(InvitationStatus.PENDING);
        verify(invitationRepository).save(any(GroupInvitation.class));
    }

    @Test
    @DisplayName("Should throw exception when inviting to non-existent group")
    void shouldThrowExceptionWhenGroupNotFound() {
        // Given
        Long groupId = 999L;
        Long invitedBy = 2L;

        InviteUserRequest request = InviteUserRequest.builder()
                .email("newuser@example.com")
                .build();

        when(groupRepository.existsById(groupId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> groupInvitationService.inviteUser(groupId, invitedBy, request))
                .isInstanceOf(GroupNotFoundException.class);

        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when neither email nor userId provided")
    void shouldThrowExceptionWhenNoIdentifierProvided() {
        // Given
        Long groupId = 1L;
        Long invitedBy = 2L;

        InviteUserRequest request = InviteUserRequest.builder().build();

        when(groupRepository.existsById(groupId)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> groupInvitationService.inviteUser(groupId, invitedBy, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("email or userId must be provided");

        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when user is already a member")
    void shouldThrowExceptionWhenUserAlreadyMember() {
        // Given
        Long groupId = 1L;
        Long invitedBy = 2L;
        Long userId = 5L;

        InviteUserRequest request = InviteUserRequest.builder()
                .userId(userId)
                .build();

        when(groupRepository.existsById(groupId)).thenReturn(true);
        when(groupMemberService.isMember(groupId, userId)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> groupInvitationService.inviteUser(groupId, invitedBy, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already a member");

        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should accept invitation successfully")
    void shouldAcceptInvitationSuccessfully() {
        // Given
        String token = "test-token";
        Long userId = 5L;

        GroupInvitation invitation = GroupInvitation.builder()
                .id(1L)
                .groupId(1L)
                .invitedBy(2L)
                .inviteeEmail("user@example.com")
                .status(InvitationStatus.PENDING)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        MemberDTO memberDTO = MemberDTO.builder()
                .id(1L)
                .groupId(1L)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();

        when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));
        when(groupMemberService.isMember(1L, userId)).thenReturn(false);
        when(groupMemberService.getMemberDetails(1L, userId)).thenReturn(memberDTO);

        // When
        MemberDTO result = groupInvitationService.acceptInvitation(token, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getRespondedAt()).isNotNull();
        verify(groupMemberService).addMemberDirectly(1L, userId, MemberRole.MEMBER, MemberStatus.ACTIVE);
        verify(groupRepository).incrementMemberCount(1L);
    }

    @Test
    @DisplayName("Should throw exception when accepting non-existent invitation")
    void shouldThrowExceptionWhenInvitationNotFound() {
        // Given
        String token = "invalid-token";
        Long userId = 5L;

        when(invitationRepository.findByToken(token)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> groupInvitationService.acceptInvitation(token, userId))
                .isInstanceOf(InvitationNotFoundException.class);

        verify(groupMemberService, never()).addMemberDirectly(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when accepting already responded invitation")
    void shouldThrowExceptionWhenInvitationAlreadyResponded() {
        // Given
        String token = "test-token";
        Long userId = 5L;

        GroupInvitation invitation = GroupInvitation.builder()
                .id(1L)
                .groupId(1L)
                .status(InvitationStatus.ACCEPTED) // Already accepted
                .token(token)
                .build();

        when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));

        // When & Then
        assertThatThrownBy(() -> groupInvitationService.acceptInvitation(token, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been accepted");

        verify(groupMemberService, never()).addMemberDirectly(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw exception when accepting expired invitation")
    void shouldThrowExceptionWhenInvitationExpired() {
        // Given
        String token = "test-token";
        Long userId = 5L;

        GroupInvitation invitation = GroupInvitation.builder()
                .id(1L)
                .groupId(1L)
                .status(InvitationStatus.PENDING)
                .token(token)
                .expiresAt(LocalDateTime.now().minusDays(1)) // Expired
                .build();

        when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));

        // When & Then
        assertThatThrownBy(() -> groupInvitationService.acceptInvitation(token, userId))
                .isInstanceOf(InvitationExpiredException.class)
                .hasMessageContaining("expired");

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verify(groupMemberService, never()).addMemberDirectly(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should decline invitation successfully")
    void shouldDeclineInvitationSuccessfully() {
        // Given
        String token = "test-token";
        Long userId = 5L;

        GroupInvitation invitation = GroupInvitation.builder()
                .id(1L)
                .groupId(1L)
                .invitedBy(2L)
                .inviteeEmail("user@example.com")
                .status(InvitationStatus.PENDING)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));

        // When
        groupInvitationService.declineInvitation(token, userId);

        // Then
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(invitation.getRespondedAt()).isNotNull();
        verify(invitationRepository).save(invitation);
    }

    @Test
    @DisplayName("Should throw exception when declining for different user")
    void shouldThrowExceptionWhenDecliningForDifferentUser() {
        // Given
        String token = "test-token";
        Long userId = 5L;

        GroupInvitation invitation = GroupInvitation.builder()
                .id(1L)
                .groupId(1L)
                .inviteeUserId(10L) // Different user
                .status(InvitationStatus.PENDING)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));

        // When & Then
        assertThatThrownBy(() -> groupInvitationService.declineInvitation(token, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("different user");

        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when user already member on invitation acceptance")
    void shouldThrowExceptionWhenUserAlreadyMemberOnAcceptance() {
        // Given
        String token = "test-token";
        Long userId = 5L;

        GroupInvitation invitation = GroupInvitation.builder()
                .id(1L)
                .groupId(1L)
                .status(InvitationStatus.PENDING)
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));
        when(groupMemberService.isMember(1L, userId)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> groupInvitationService.acceptInvitation(token, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already a member");

        verify(groupMemberService, never()).addMemberDirectly(any(), any(), any(), any());
    }
}
