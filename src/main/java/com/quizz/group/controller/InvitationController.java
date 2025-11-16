package com.quizz.group.controller;

import com.quizz.group.dto.InvitationDTO;
import com.quizz.group.dto.MemberDTO;
import com.quizz.group.security.UserPrincipal;
import com.quizz.group.service.GroupInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for managing group invitations.
 */
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Group Invitations", description = "APIs for managing group invitations")
@SecurityRequirement(name = "JWT Cookie Authentication")
public class InvitationController {

    private final GroupInvitationService groupInvitationService;

    @Operation(
            summary = "Get current user's pending invitations",
            description = "Retrieves all pending (non-expired) invitations for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitations retrieved successfully")
    })
    @GetMapping("/my-invitations")
    public ResponseEntity<List<InvitationDTO>> getMyInvitations(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.debug("Getting pending invitations for user: {} ({})", principal.getUserId(), principal.getEmail());

        // Get invitations by both email and userId to cover all cases
        List<InvitationDTO> invitations = new ArrayList<>();

        // Get invitations sent to user's email
        if (principal.getEmail() != null) {
            invitations.addAll(groupInvitationService.getPendingInvitationsByEmail(principal.getEmail()));
        }

        // Get invitations sent to user's ID
        invitations.addAll(groupInvitationService.getPendingInvitationsByUserId(principal.getUserId()));

        // Remove duplicates (in case invitation was sent with both email and userId)
        List<InvitationDTO> uniqueInvitations = invitations.stream()
                .distinct()
                .collect(Collectors.toList());

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
    @PostMapping("/{token}/accept")
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
    @PostMapping("/{token}/decline")
    public ResponseEntity<Map<String, String>> declineInvitation(
            @Parameter(description = "Invitation token") @PathVariable String token,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("User {} declining invitation with token: {}", principal.getUserId(), token);
        groupInvitationService.declineInvitation(token, principal.getUserId());
        return ResponseEntity.ok(Map.of("message", "Invitation declined"));
    }
}
