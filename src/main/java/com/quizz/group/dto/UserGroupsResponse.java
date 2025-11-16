package com.quizz.group.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Response containing list of group IDs for a user.
 * Used for Question Service integration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "List of group IDs for a user")
public class UserGroupsResponse {

    @Schema(description = "List of group IDs", example = "[1, 2, 3]")
    private List<Long> groupIds;

    public List<Long> getGroupIds() {
        return groupIds != null ? groupIds : Collections.emptyList();
    }
}
