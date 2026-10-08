package com.nexhome.app.dto;

import com.nexhome.app.model.NexHomeUserStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Admin decision on a user: only {@code APPROVED} or {@code REJECTED} can be set here. {@code ACTIVATED} is reached
 * only when the approved user sets a password.
 */
public record NexHomeUserStatusUpdateRequest(
		@NotNull(message = "User ID is required")
		Long userId,

		@NotNull(message = "User status is required")
		NexHomeUserStatus userStatus) {
}
