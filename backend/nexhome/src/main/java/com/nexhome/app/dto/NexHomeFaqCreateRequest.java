package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates an FAQ: general, or for one category or one service (not both). Unpublished until {@code isPublished} is
 * true.
 */
public record NexHomeFaqCreateRequest(
		@NotBlank(message = "Question is required")
		@Size(max = 500, message = "Question must be at most 500 characters")
		String question,

		@NotBlank(message = "Answer is required")
		@Size(max = 10000, message = "Answer must be at most 10000 characters")
		String answer,

		Long categoryId,

		Long serviceId,

		@NotBlank(message = "Audience is required")
		String audience,

		Integer displayOrder,

		Boolean isPublished) {
}
