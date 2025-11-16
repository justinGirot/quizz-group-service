package com.quizz.group.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to invite a user to a group.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to invite a user to a group")
public class InviteUserRequest {

    @Email(message = "Invalid email format")
    @Schema(description = "Email of user to invite (if not registered)", example = "newuser@example.com")
    private String email;

    @Schema(description = "User ID to invite (if registered user)", example = "5")
    private Long userId;

    @Schema(description = "Optional invitation message", example = "Join our team!")
    private String message;
}
