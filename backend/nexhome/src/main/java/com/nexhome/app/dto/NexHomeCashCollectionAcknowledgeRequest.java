package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The customer confirms they paid the cash.
 */
public record NexHomeCashCollectionAcknowledgeRequest(
		@NotNull(message = "Cash collection ID is required")
		Long cashCollectionId) {
}
