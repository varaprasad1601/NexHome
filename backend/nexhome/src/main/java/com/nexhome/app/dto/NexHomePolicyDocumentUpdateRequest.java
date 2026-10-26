package com.nexhome.app.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates an unpublished policy version; a published one never changes (create a new version). A null (or missing)
 * field keeps its current value; the title and content cannot be blanked. The key and version identify the document and
 * the published flag has its own endpoint: they are only declared here so that a request containing them is rejected
 * with a clear message.
 */
public record NexHomePolicyDocumentUpdateRequest(
		@NotNull(message = "Policy document ID is required")
		Long policyDocumentId,

		@Null(message = "Policy key cannot be updated")
		String policyKey,

		@Null(message = "Version cannot be updated")
		String version,

		@Null(message = "Use the publish endpoint to publish a policy")
		Boolean isPublished,

		@Pattern(regexp = "(?s).*\\S.*", message = "Title cannot be blank")
		@Size(max = 200, message = "Title must be at most 200 characters")
		String title,

		String audience,

		@Pattern(regexp = "(?s).*\\S.*", message = "Content cannot be blank")
		String content,

		Instant effectiveFrom) {
}
