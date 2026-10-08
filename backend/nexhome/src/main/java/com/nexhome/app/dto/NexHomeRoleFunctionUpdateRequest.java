package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

/**
 * Updates a role function's access. A null flag keeps its current value; write or delete also grants read.
 * The role and function identify the row and cannot be changed: they are only declared here so that a request
 * containing them is rejected with a clear message instead of being silently ignored.
 */
public record NexHomeRoleFunctionUpdateRequest(
		@NotNull(message = "Role function ID is required")
		Long roleFunctionId,

		@Null(message = "Role cannot be updated")
		Long roleId,

		@Null(message = "Function cannot be updated")
		Long functionId,

		Boolean read,

		Boolean write,

		Boolean delete,

		Boolean activeFlag) {
}
