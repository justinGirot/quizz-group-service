package com.quizz.group.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to update the group admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to assign/change group admin")
public class UpdateGroupAdminRequest {

    @NotNull(message = "Group admin ID is required")
    @Schema(description = "User ID of the new group admin", example = "3", required = true)
    private Long groupAdminId;
}
