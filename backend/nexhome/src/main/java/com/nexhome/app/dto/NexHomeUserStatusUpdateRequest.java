package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param accountStatus an active ACCOUNT_STATUS lookup code, e.g. ACTIVE, SUSPENDED, BLOCKED or DEACTIVATED
 */
public record NexHomeUserStatusUpdateRequest(
		@NotNull(message = "User ID is required")
		Long userId,

		@NotBlank(message = "Account status is required")
		String accountStatus) {
}
