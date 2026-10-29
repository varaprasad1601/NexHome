package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates a category. A null (or missing) field keeps its current value; the name and slug cannot be blanked, and an
 * empty description or icon URL clears it. A category that services use is deactivated rather than deleted.
 */
public record NexHomeServiceCategoryUpdateRequest(
		@NotNull(message = "Service category ID is required")
		Long serviceCategoryId,

		@Null(message = "Public ID cannot be updated")
		String publicId,

		@Pattern(regexp = "(?s).*\\S.*", message = "Name cannot be blank")
		@Size(max = 120, message = "Name must be at most 120 characters")
		String name,

		@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
				message = "Slug must be lower-case letters and digits separated by single hyphens")
		@Size(max = 150, message = "Slug must be at most 150 characters")
		String slug,

		@Size(max = 300, message = "Description must be at most 300 characters")
		String description,

		@Pattern(regexp = "^$|^https?://\\S+$", message = "Icon URL must be an http or https URL")
		String iconUrl,

		Integer displayOrder,

		Boolean isActive) {
}
