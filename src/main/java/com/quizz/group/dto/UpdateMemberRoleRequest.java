package com.quizz.group.dto;

import com.quizz.group.model.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to change a member's role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to change member role")
public class UpdateMemberRoleRequest {

    @NotNull(message = "Role is required")
    @Schema(description = "New member role (MEMBER or ADMIN)", example = "ADMIN", required = true)
    private MemberRole role;
}
