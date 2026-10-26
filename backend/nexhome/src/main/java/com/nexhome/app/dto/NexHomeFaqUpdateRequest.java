package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates an FAQ. A null (or missing) field keeps its current value; the question and answer cannot be blanked. The
 * category and service are fixed: they are only declared here so that a request containing them is rejected with a
 * clear message.
 */
public record NexHomeFaqUpdateRequest(
		@NotNull(message = "FAQ ID is required")
		Long faqId,

		@Null(message = "Category cannot be updated")
		Long categoryId,

		@Null(message = "Service cannot be updated")
		Long serviceId,

		@Pattern(regexp = "(?s).*\\S.*", message = "Question cannot be blank")
		@Size(max = 500, message = "Question must be at most 500 characters")
		String question,

		@Pattern(regexp = "(?s).*\\S.*", message = "Answer cannot be blank")
		@Size(max = 10000, message = "Answer must be at most 10000 characters")
		String answer,

		String audience,

		Integer displayOrder,

		Boolean isPublished) {
}
