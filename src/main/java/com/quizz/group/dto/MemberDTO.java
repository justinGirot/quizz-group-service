package com.quizz.group.dto;

import com.quizz.group.model.MemberRole;
import com.quizz.group.model.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * DTO for Group Member information.
 */
@Value
@Builder
@Schema(description = "Group member information")
public class MemberDTO {

    @Schema(description = "Member record ID", example = "1")
    Long id;

    @Schema(description = "Group ID", example = "1")
    Long groupId;

    @Schema(description = "User ID", example = "5")
    Long userId;

    @Schema(description = "User display name", example = "Jane Smith")
    String userDisplayName;

    @Schema(description = "User avatar URL", example = "https://example.com/user-avatar.png")
    String userAvatarUrl;

    @Schema(description = "Member role in the group", example = "MEMBER")
    MemberRole role;

    @Schema(description = "Member status", example = "ACTIVE")
    MemberStatus status;

    @Schema(description = "Timestamp when user joined the group")
    LocalDateTime joinedAt;
}
