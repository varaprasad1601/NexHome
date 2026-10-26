package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Publishes a policy version. Publishing cannot be undone.
 */
public record NexHomePolicyDocumentPublishRequest(
		@NotNull(message = "Policy document ID is required")
		Long policyDocumentId) {
}
