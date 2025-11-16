package com.quizz.group.service;

import com.quizz.group.dto.CreateGroupRequest;
import com.quizz.group.dto.GroupDTO;
import com.quizz.group.dto.UpdateGroupRequest;
import com.quizz.group.exception.GroupAlreadyExistsException;
import com.quizz.group.exception.GroupNotFoundException;
import com.quizz.group.model.Group;
import com.quizz.group.model.GroupType;
import com.quizz.group.model.MemberRole;
import com.quizz.group.model.MemberStatus;
import com.quizz.group.repository.GroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberService groupMemberService;

    @InjectMocks
    private GroupService groupService;

    @Test
    @DisplayName("Should create group successfully with group admin as first member")
    void shouldCreateGroupSuccessfully() {
        // Given
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Engineering Team")
                .description("Team for engineers")
                .type(GroupType.PUBLIC)
                .groupAdminId(2L)
                .build();

        Group savedGroup = Group.builder()
                .id(1L)
                .name("Engineering Team")
                .description("Team for engineers")
                .type(GroupType.PUBLIC)
                .createdBy(1L)
                .groupAdminId(2L)
                .memberCount(1)
                .build();

        when(groupRepository.existsByName("Engineering Team")).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenReturn(savedGroup);

        // When
        GroupDTO result = groupService.createGroup(request, 1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Engineering Team");
        assertThat(result.getType()).isEqualTo(GroupType.PUBLIC);
        assertThat(result.getGroupAdminId()).isEqualTo(2L);
        verify(groupRepository).save(any(Group.class));
        verify(groupMemberService).addMemberDirectly(1L, 2L, MemberRole.ADMIN, MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw exception when group name already exists")
    void shouldThrowExceptionWhenGroupNameExists() {
        // Given
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Existing Group")
                .type(GroupType.PUBLIC)
                .groupAdminId(2L)
                .build();

        when(groupRepository.existsByName("Existing Group")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> groupService.createGroup(request, 1L))
                .isInstanceOf(GroupAlreadyExistsException.class)
                .hasMessageContaining("Existing Group");

        verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update group details successfully")
    void shouldUpdateGroupSuccessfully() {
        // Given
        Long groupId = 1L;
        UpdateGroupRequest request = UpdateGroupRequest.builder()
                .name("Updated Name")
                .description("Updated description")
                .build();

        Group existingGroup = Group.builder()
                .id(groupId)
                .name("Old Name")
                .description("Old description")
                .type(GroupType.PUBLIC)
                .createdBy(1L)
                .groupAdminId(2L)
                .memberCount(5)
                .build();

        Group updatedGroup = Group.builder()
                .id(groupId)
                .name("Updated Name")
                .description("Updated description")
                .type(GroupType.PUBLIC)
                .createdBy(1L)
                .groupAdminId(2L)
                .memberCount(5)
                .build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(existingGroup));
        when(groupRepository.existsByName("Updated Name")).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenReturn(updatedGroup);

        // When
        GroupDTO result = groupService.updateGroup(groupId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getDescription()).isEqualTo("Updated description");
        verify(groupRepository).save(any(Group.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent group")
    void shouldThrowExceptionWhenUpdatingNonExistentGroup() {
        // Given
        Long groupId = 999L;
        UpdateGroupRequest request = UpdateGroupRequest.builder()
                .name("Updated Name")
                .build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> groupService.updateGroup(groupId, request))
                .isInstanceOf(GroupNotFoundException.class);

        verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete group successfully")
    void shouldDeleteGroupSuccessfully() {
        // Given
        Long groupId = 1L;
        when(groupRepository.existsById(groupId)).thenReturn(true);

        // When
        groupService.deleteGroup(groupId);

        // Then
        verify(groupRepository).deleteById(groupId);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent group")
    void shouldThrowExceptionWhenDeletingNonExistentGroup() {
        // Given
        Long groupId = 999L;
        when(groupRepository.existsById(groupId)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> groupService.deleteGroup(groupId))
                .isInstanceOf(GroupNotFoundException.class);

        verify(groupRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should get group by ID successfully")
    void shouldGetGroupById() {
        // Given
        Long groupId = 1L;
        Group group = Group.builder()
                .id(groupId)
                .name("Test Group")
                .type(GroupType.PUBLIC)
                .createdBy(1L)
                .groupAdminId(2L)
                .memberCount(10)
                .build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // When
        GroupDTO result = groupService.getGroupById(groupId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(groupId);
        assertThat(result.getName()).isEqualTo("Test Group");
    }

    @Test
    @DisplayName("Should throw exception when group not found by ID")
    void shouldThrowExceptionWhenGroupNotFoundById() {
        // Given
        Long groupId = 999L;
        when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> groupService.getGroupById(groupId))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    @DisplayName("Should get public groups with pagination")
    void shouldGetPublicGroups() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        List<Group> groups = List.of(
                Group.builder().id(1L).name("Group 1").type(GroupType.PUBLIC).build(),
                Group.builder().id(2L).name("Group 2").type(GroupType.PUBLIC).build()
        );
        Page<Group> groupPage = new PageImpl<>(groups, pageable, 2);

        when(groupRepository.findByType(GroupType.PUBLIC, pageable)).thenReturn(groupPage);

        // When
        Page<GroupDTO> result = groupService.getPublicGroups(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should check if user is group admin")
    void shouldCheckIfUserIsGroupAdmin() {
        // Given
        Long groupId = 1L;
        Long userId = 2L;
        Group group = Group.builder()
                .id(groupId)
                .name("Test Group")
                .groupAdminId(userId)
                .build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // When
        boolean result = groupService.isGroupAdmin(groupId, userId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when user is not group admin")
    void shouldReturnFalseWhenUserIsNotGroupAdmin() {
        // Given
        Long groupId = 1L;
        Long userId = 3L;
        Group group = Group.builder()
                .id(groupId)
                .name("Test Group")
                .groupAdminId(2L) // Different user
                .build();

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // When
        boolean result = groupService.isGroupAdmin(groupId, userId);

        // Then
        assertThat(result).isFalse();
    }
}
