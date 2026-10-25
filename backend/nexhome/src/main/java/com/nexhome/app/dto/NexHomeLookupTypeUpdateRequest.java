package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a lookup type. A null (or missing) name or active flag keeps its current value and the name cannot be
 * blanked; the description is always replaced, so leaving it out clears it. The type code and the system-managed
 * flag are fixed: they are only declared here so that a request containing them is rejected with a clear message
 * instead of being silently ignored.
 */
public record NexHomeLookupTypeUpdateRequest(
		@NotNull(message = "Lookup type ID is required")
		Long lookupTypeId,

		@Null(message = "Type code cannot be updated")
		String typeCode,

		@Pattern(regexp = "(?s).*\\S.*", message = "Type name cannot be blank")
		@Size(max = 120, message = "Type name must be at most 120 characters")
		String typeName,

		String description,

		Boolean activeFlag,

		@Null(message = "System managed flag cannot be updated")
		Boolean isSystemManaged) {
}
