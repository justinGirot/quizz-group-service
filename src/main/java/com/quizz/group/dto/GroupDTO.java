package com.quizz.group.dto;

import com.quizz.group.model.GroupType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * DTO for Group information.
 */
@Value
@Builder
@Schema(description = "Group information")
public class GroupDTO {

    @Schema(description = "Group ID", example = "1")
    Long id;

    @Schema(description = "Group name", example = "Engineering Team")
    String name;

    @Schema(description = "Group description", example = "Team for all engineers")
    String description;

    @Schema(description = "Group type", example = "PUBLIC")
    GroupType type;

    @Schema(description = "ID of user who created the group", example = "1")
    Long createdBy;

    @Schema(description = "ID of the designated group admin", example = "2")
    Long groupAdminId;

    @Schema(description = "Group admin display name", example = "John Doe")
    String groupAdminName;

    @Schema(description = "Group avatar URL", example = "https://example.com/avatar.png")
    String avatarUrl;

    @Schema(description = "Number of members in the group", example = "15")
    Integer memberCount;

    @Schema(description = "Group creation timestamp")
    LocalDateTime createdAt;

    @Schema(description = "Group last update timestamp")
    LocalDateTime updatedAt;
}
