package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Gives a role access to a function. read, write and delete default to false; write or delete also grants read.
 *
 * @param activeFlag defaults to true when missing
 */
public record NexHomeRoleFunctionCreateRequest(
		@NotNull(message = "Role is required")
		Long roleId,

		@NotNull(message = "Function is required")
		Long functionId,

		Boolean read,

		Boolean write,

		Boolean delete,

		Boolean activeFlag) {
}
