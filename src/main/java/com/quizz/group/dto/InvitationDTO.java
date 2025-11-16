package com.quizz.group.dto;

import com.quizz.group.model.InvitationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * DTO for Group Invitation information.
 */
@Value
@Builder
@Schema(description = "Group invitation information")
public class InvitationDTO {

    @Schema(description = "Invitation ID", example = "1")
    Long id;

    @Schema(description = "Group ID", example = "1")
    Long groupId;

    @Schema(description = "Group name", example = "Engineering Team")
    String groupName;

    @Schema(description = "ID of user who sent the invitation", example = "2")
    Long invitedBy;

    @Schema(description = "Name of user who sent the invitation", example = "John Doe")
    String invitedByName;

    @Schema(description = "Email of invitee (if not registered)", example = "newuser@example.com")
    String inviteeEmail;

    @Schema(description = "User ID of invitee (if registered)", example = "5")
    Long inviteeUserId;

    @Schema(description = "Invitation status", example = "PENDING")
    InvitationStatus status;

    @Schema(description = "Unique invitation token", example = "abc123xyz")
    String token;

    @Schema(description = "Invitation expiration timestamp")
    LocalDateTime expiresAt;

    @Schema(description = "Invitation creation timestamp")
    LocalDateTime createdAt;
}
