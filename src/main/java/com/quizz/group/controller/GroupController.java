package com.quizz.group.controller;

import com.quizz.group.dto.*;
import com.quizz.group.security.UserPrincipal;
import com.quizz.group.service.GroupInvitationService;
import com.quizz.group.service.GroupMemberService;
import com.quizz.group.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for group management operations.
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Group Management", description = "APIs for managing groups and memberships")
@SecurityRequirement(name = "JWT Cookie Authentication")
public class GroupController {

    private final GroupService groupService;
    private final GroupMemberService groupMemberService;
    private final GroupInvitationService groupInvitationService;

    @Operation(
            summary = "Create a new group",
            description = "Creates a new group with the specified details. Only application admins can create groups."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Group created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "409", description = "Group name already exists")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupDTO> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("Creating group: {}", request.getName());
        GroupDTO group = groupService.createGroup(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    @Operation(
            summary = "Update group details",
            description = "Updates group information. Only application admins can update groups."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupDTO> updateGroup(
            @Parameter(description = "Group ID") @PathVariable Long id,
            @Valid @RequestBody UpdateGroupRequest request
    ) {
        log.info("Updating group: {}", id);
        GroupDTO group = groupService.updateGroup(id, request);
        return ResponseEntity.ok(group);
    }

    @Operation(
            summary = "Delete a group",
            description = "Deletes a group. Only application admins can delete groups."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteGroup(
            @Parameter(description = "Group ID") @PathVariable Long id
    ) {
        log.info("Deleting group: {}", id);
        groupService.deleteGroup(id);
        return ResponseEntity.ok(Map.of("message", "Group deleted successfully"));
    }

    @Operation(
            summary = "Update group admin",
            description = "Assigns or changes the group admin. Only application admins can perform this operation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group admin updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @PutMapping("/{id}/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupDTO> updateGroupAdmin(
            @Parameter(description = "Group ID") @PathVariable Long id,
            @Valid @RequestBody UpdateGroupAdminRequest request
    ) {
        log.info("Updating group admin for group: {}", id);
        GroupDTO group = groupService.updateGroupAdmin(id, request);
        return ResponseEntity.ok(group);
    }

    @Operation(
            summary = "Get group by ID",
            description = "Retrieves detailed information about a specific group."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group found"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<GroupDTO> getGroup(
            @Parameter(description = "Group ID") @PathVariable Long id
    ) {
        log.debug("Getting group: {}", id);
        GroupDTO group = groupService.getGroupById(id);
        return ResponseEntity.ok(group);
    }

    @Operation(
            summary = "List all public groups",
            description = "Retrieves a paginated list of all public groups."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Groups retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<Page<GroupDTO>> getPublicGroups(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.debug("Getting public groups");
        Page<GroupDTO> groups = groupService.getPublicGroups(pageable);
        return ResponseEntity.ok(groups);
    }

    @Operation(
            summary = "Search groups",
            description = "Searches groups by name with pagination support."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search completed successfully")
    })
    @GetMapping("/search")
    public ResponseEntity<Page<GroupDTO>> searchGroups(
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        log.debug("Searching groups with term: {}", search);
        Page<GroupDTO> groups = groupService.searchGroups(search, pageable);
        return ResponseEntity.ok(groups);
    }

    @Operation(
            summary = "Join a public group",
            description = "Allows a user to join a public group."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Successfully joined group"),
            @ApiResponse(responseCode = "400", description = "Cannot join private group or already a member"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @PostMapping("/{id}/join")
    public ResponseEntity<MemberDTO> joinGroup(
            @Parameter(description = "Group ID") @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("User {} joining group {}", principal.getUserId(), id);
        MemberDTO member = groupMemberService.joinGroup(id, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @Operation(
            summary = "Leave a group",
            description = "Allows a user to leave a group they are a member of."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully left group"),
            @ApiResponse(responseCode = "400", description = "Not a member or already removed"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @PostMapping("/{id}/leave")
    public ResponseEntity<Map<String, String>> leaveGroup(
            @Parameter(description = "Group ID") @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("User {} leaving group {}", principal.getUserId(), id);
        groupMemberService.leaveGroup(id, principal.getUserId());
        return ResponseEntity.ok(Map.of("message", "Successfully left group"));
    }

    @Operation(
            summary = "Get current user's groups",
            description = "Retrieves all groups that the current user is a member of."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Groups retrieved successfully")
    })
    @GetMapping("/my-groups")
    public ResponseEntity<List<MemberDTO>> getMyGroups(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.debug("Getting groups for user: {}", principal.getUserId());
        List<MemberDTO> groups = groupMemberService.getUserGroups(principal.getUserId());
        return ResponseEntity.ok(groups);
    }

    @Operation(
            summary = "Get group members",
            description = "Retrieves a paginated list of active members in a group."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Members retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @GetMapping("/{id}/members")
    public ResponseEntity<Page<MemberDTO>> getGroupMembers(
            @Parameter(description = "Group ID") @PathVariable Long id,
            @PageableDefault(size = 20, sort = "joinedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.debug("Getting members for group: {}", id);
        Page<MemberDTO> members = groupMemberService.getGroupMembers(id, pageable);
        return ResponseEntity.ok(members);
    }

    @Operation(
            summary = "Invite user to group",
            description = "Invites a user to join a group. Group admin or app admin only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Invitation sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or user already member"),
            @ApiResponse(responseCode = "403", description = "Forbidden - not authorized"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @PostMapping("/{id}/invite")
    @PreAuthorize("hasRole('ADMIN') or @groupService.isGroupAdmin(#id, principal.userId)")
    public ResponseEntity<InvitationDTO> inviteUser(
            @Parameter(description = "Group ID") @PathVariable Long id,
            @Valid @RequestBody InviteUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("Inviting user to group {}", id);
        InvitationDTO invitation = groupInvitationService.inviteUser(id, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
    }

    @Operation(
            summary = "Remove member from group",
            description = "Removes a member from a group. Group admin or app admin only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Member removed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - not authorized"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @groupService.isGroupAdmin(#id, principal.userId)")
    public ResponseEntity<Map<String, String>> removeMember(
            @Parameter(description = "Group ID") @PathVariable Long id,
            @Parameter(description = "User ID to remove") @PathVariable Long userId
    ) {
        log.info("Removing user {} from group {}", userId, id);
        groupMemberService.removeMember(id, userId);
        return ResponseEntity.ok(Map.of("message", "Member removed successfully"));
    }

    @Operation(
            summary = "Update member role",
            description = "Changes a member's role in the group. Group admin or app admin only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Member role updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - not authorized"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @PutMapping("/{id}/members/{userId}/role")
    @PreAuthorize("hasRole('ADMIN') or @groupService.isGroupAdmin(#id, principal.userId)")
    public ResponseEntity<MemberDTO> updateMemberRole(
            @Parameter(description = "Group ID") @PathVariable Long id,
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        log.info("Updating role for user {} in group {}", userId, id);
        MemberDTO member = groupMemberService.updateMemberRole(id, userId, request);
        return ResponseEntity.ok(member);
    }

    @Operation(
            summary = "Get current user's pending invitations",
            description = "Retrieves all pending (non-expired) invitations for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitations retrieved successfully")
    })
    @GetMapping("/invitations/my-invitations")
    public ResponseEntity<List<InvitationDTO>> getMyInvitations(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.debug("Getting pending invitations for user: {} ({})", principal.getUserId(), principal.getEmail());

        // Get invitations by both email and userId to cover all cases
        List<InvitationDTO> invitations = new java.util.ArrayList<>();

        // Get invitations sent to user's email
        if (principal.getEmail() != null) {
            invitations.addAll(groupInvitationService.getPendingInvitationsByEmail(principal.getEmail()));
        }

        // Get invitations sent to user's ID
        invitations.addAll(groupInvitationService.getPendingInvitationsByUserId(principal.getUserId()));

        // Remove duplicates (in case invitation was sent with both email and userId)
        List<InvitationDTO> uniqueInvitations = invitations.stream()
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        log.debug("Found {} unique pending invitations for user {}", uniqueInvitations.size(), principal.getUserId());
        return ResponseEntity.ok(uniqueInvitations);
    }

    @Operation(
            summary = "Accept group invitation",
            description = "Accepts a group invitation using the invitation token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation accepted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid invitation or already responded"),
            @ApiResponse(responseCode = "404", description = "Invitation not found"),
            @ApiResponse(responseCode = "410", description = "Invitation expired")
    })
    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<MemberDTO> acceptInvitation(
            @Parameter(description = "Invitation token") @PathVariable String token,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("User {} accepting invitation with token: {}", principal.getUserId(), token);
        MemberDTO member = groupInvitationService.acceptInvitation(token, principal.getUserId());
        return ResponseEntity.ok(member);
    }

    @Operation(
            summary = "Decline group invitation",
            description = "Declines a group invitation using the invitation token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation declined successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid invitation or already responded"),
            @ApiResponse(responseCode = "404", description = "Invitation not found"),
            @ApiResponse(responseCode = "410", description = "Invitation expired")
    })
    @PostMapping("/invitations/{token}/decline")
    public ResponseEntity<Map<String, String>> declineInvitation(
            @Parameter(description = "Invitation token") @PathVariable String token,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("User {} declining invitation with token: {}", principal.getUserId(), token);
        groupInvitationService.declineInvitation(token, principal.getUserId());
        return ResponseEntity.ok(Map.of("message", "Invitation declined"));
    }

    // Service-to-service endpoints

    @Operation(
            summary = "Check user membership",
            description = "Service-to-service endpoint to check if a user is a member of a group."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Membership status returned"),
            @ApiResponse(responseCode = "404", description = "Group or member not found")
    })
    @GetMapping("/{id}/members/{userId}")
    public ResponseEntity<Map<String, Object>> checkMembership(
            @Parameter(description = "Group ID") @PathVariable Long id,
            @Parameter(description = "User ID") @PathVariable Long userId
    ) {
        boolean isMember = groupMemberService.isMember(id, userId);
        MemberDTO member = null;
        if (isMember) {
            member = groupMemberService.getMemberDetails(id, userId);
        }
        return ResponseEntity.ok(Map.of(
                "isMember", isMember,
                "role", member != null ? member.getRole() : ""
        ));
    }

    @Operation(
            summary = "Validate group",
            description = "Service-to-service endpoint to validate a group exists and get its type."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group validation successful"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @GetMapping("/{id}/validate")
    public ResponseEntity<Map<String, String>> validateGroup(
            @Parameter(description = "Group ID") @PathVariable Long id
    ) {
        GroupDTO group = groupService.getGroupById(id);
        return ResponseEntity.ok(Map.of("type", group.getType().name()));
    }

    @Operation(
            summary = "Get user's group IDs",
            description = "Service-to-service endpoint. Returns all group IDs where user is an active member (any role)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group IDs retrieved successfully")
    })
    @GetMapping("/user/{userId}/groups")
    public ResponseEntity<UserGroupsResponse> getUserGroups(
            @Parameter(description = "User ID") @PathVariable Long userId
    ) {
        log.info("Getting group IDs for userId={}", userId);
        List<Long> groupIds = groupMemberService.getUserGroupIds(userId);
        return ResponseEntity.ok(new UserGroupsResponse(groupIds));
    }

    @Operation(
            summary = "Get user's admin group IDs",
            description = "Service-to-service endpoint. Returns group IDs where user has ADMIN role. Used by Question Service for access control."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Admin group IDs retrieved successfully")
    })
    @GetMapping("/user/{userId}/admin-groups")
    public ResponseEntity<UserGroupsResponse> getUserAdminGroups(
            @Parameter(description = "User ID") @PathVariable Long userId
    ) {
        log.info("Getting admin group IDs for userId={}", userId);
        List<Long> adminGroupIds = groupMemberService.getUserAdminGroupIds(userId);
        return ResponseEntity.ok(new UserGroupsResponse(adminGroupIds));
    }
}
