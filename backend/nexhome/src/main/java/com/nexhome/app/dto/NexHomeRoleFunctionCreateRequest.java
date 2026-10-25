package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param canRead defaults to false; also granted by canWrite or canDelete
 * @param canWrite defaults to false
 * @param canDelete defaults to false
 * @param activeFlag defaults to true when missing
 */
public record NexHomeRoleFunctionCreateRequest(
		@NotNull(message = "Role is required")
		Short roleId,

		@NotNull(message = "Function is required")
		Long functionId,

		Boolean canRead,

		Boolean canWrite,

		Boolean canDelete,

		Boolean activeFlag) {
}
