package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A service (and variant) for the signed-in customer's cart.
 */
public record NexHomeCartItemAddRequest(
		@NotNull(message = "Service ID is required")
		Long serviceId,

		Long serviceVariantId) {
}
