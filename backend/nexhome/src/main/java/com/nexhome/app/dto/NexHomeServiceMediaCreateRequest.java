package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Adds an image or video to a service by its storage (or CDN) key.
 */
public record NexHomeServiceMediaCreateRequest(
		@NotNull(message = "Service is required")
		Long serviceId,

		@NotBlank(message = "Media type is required")
		String mediaType,

		@NotBlank(message = "Storage key is required")
		@Size(max = 1024, message = "Storage key must be at most 1024 characters")
		String storageKey,

		Integer displayOrder,

		Boolean isActive) {
}
