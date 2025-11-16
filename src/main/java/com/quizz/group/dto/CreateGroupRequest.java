package com.quizz.group.dto;

import com.quizz.group.model.GroupType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to create a new group.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new group")
public class CreateGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(min = 1, max = 100, message = "Group name must be between 1 and 100 characters")
    @Schema(description = "Group name", example = "Engineering Team", required = true)
    private String name;

    @Schema(description = "Group description", example = "Team for all engineers")
    private String description;

    @NotNull(message = "Group type is required")
    @Schema(description = "Group type (PUBLIC or PRIVATE)", example = "PUBLIC", required = true)
    private GroupType type;

    @NotNull(message = "Group admin ID is required")
    @Schema(description = "User ID of the designated group admin", example = "2", required = true)
    private Long groupAdminId;

    @Schema(description = "Group avatar URL", example = "https://example.com/avatar.png")
    private String avatarUrl;
}
