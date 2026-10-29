package com.nexhome.app.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Puts a service's images in this order; the first is the cover. Lists every image of the service exactly once.
 */
public record NexHomeServiceImageOrderRequest(
		@NotNull(message = "Service is required")
		Long serviceId,

		@NotEmpty(message = "Image order is required")
		List<@NotNull(message = "Image ID is required") Long> serviceImageIds) {
}
