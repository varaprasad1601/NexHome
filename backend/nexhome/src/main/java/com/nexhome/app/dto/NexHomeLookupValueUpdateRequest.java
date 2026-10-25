package com.nexhome.app.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a lookup value. A null (or missing) field keeps its current value, except the description, which is always
 * replaced. The display name cannot be blanked. {@code isDefault: true} makes this the type's default (replacing the
 * current one); {@code false} removes the default. The type and code identify the value and cannot be changed: they
 * are only declared here so that a request containing them is rejected with a clear message.
 */
public record NexHomeLookupValueUpdateRequest(
		@NotNull(message = "Lookup value ID is required")
		Long lookupValueId,

		@Null(message = "Lookup type cannot be updated")
		Long lookupTypeId,

		@Null(message = "Value code cannot be updated")
		String valueCode,

		@Pattern(regexp = "(?s).*\\S.*", message = "Display name cannot be blank")
		@Size(max = 150, message = "Display name must be at most 150 characters")
		String displayName,

		String description,

		Integer displayOrder,

		Boolean activeFlag,

		Boolean isDefault,

		Map<String, Object> metadata) {
}
