package com.nexhome.app.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates an unpublished version of a policy. {@code effectiveFrom} is an ISO-8601 instant, e.g.
 * 2026-11-01T00:00:00+05:30. The key and version identify the document and are unique together.
 */
public record NexHomePolicyDocumentCreateRequest(
		@NotBlank(message = "Policy key is required")
		@Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
				message = "Policy key must contain only capital letters, digits and underscores, starting with a letter")
		@Size(max = 80, message = "Policy key must be at most 80 characters")
		String policyKey,

		@NotBlank(message = "Title is required")
		@Size(max = 200, message = "Title must be at most 200 characters")
		String title,

		@NotBlank(message = "Audience is required")
		String audience,

		@NotBlank(message = "Version is required")
		@Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
				message = "Version must contain only letters, digits, dots, hyphens and underscores")
		@Size(max = 30, message = "Version must be at most 30 characters")
		String version,

		@NotBlank(message = "Content is required")
		String content,

		@NotNull(message = "Effective from is required")
		Instant effectiveFrom) {
}
