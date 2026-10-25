package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a role. A null (or missing) name or active flag keeps its current value and the name cannot be blanked; the
 * description is always replaced, so leaving it out clears it. The code identifies the role and cannot be changed: it
 * is only declared here so that a request containing it is rejected with a clear message.
 */
public record NexHomeRoleUpdateRequest(
		@NotNull(message = "Role ID is required")
		Short roleId,

		@Null(message = "Role code cannot be updated")
		String code,

		@Pattern(regexp = "(?s).*\\S.*", message = "Role name cannot be blank")
		@Size(max = 80, message = "Role name must be at most 80 characters")
		String name,

		String description,

		Boolean activeFlag) {
}
