package com.quizz.group.dto;

import com.quizz.group.model.GroupType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to update group details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update group details")
public class UpdateGroupRequest {

    @Size(min = 1, max = 100, message = "Group name must be between 1 and 100 characters")
    @Schema(description = "Group name", example = "Updated Team Name")
    private String name;

    @Schema(description = "Group description", example = "Updated description")
    private String description;

    @Schema(description = "Group type (PUBLIC or PRIVATE)", example = "PRIVATE")
    private GroupType type;

    @Schema(description = "Group avatar URL", example = "https://example.com/new-avatar.png")
    private String avatarUrl;
}
