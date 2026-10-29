package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates a service category. The slug defaults to the name in lower case with hyphens, e.g. "Home Cleaning" ->
 * home-cleaning; it must be unique.
 */
public record NexHomeServiceCategoryCreateRequest(
		@NotBlank(message = "Name is required")
		@Size(max = 120, message = "Name must be at most 120 characters")
		String name,

		@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
				message = "Slug must be lower-case letters and digits separated by single hyphens")
		@Size(max = 150, message = "Slug must be at most 150 characters")
		String slug,

		@Size(max = 300, message = "Description must be at most 300 characters")
		String description,

		@Pattern(regexp = "^https?://\\S+$", message = "Icon URL must be an http or https URL")
		String iconUrl,

		Integer displayOrder,

		Boolean isActive) {
}
