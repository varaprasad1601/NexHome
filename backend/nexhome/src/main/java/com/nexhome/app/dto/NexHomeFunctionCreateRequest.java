package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param activeFlag defaults to true when missing
 */
public record NexHomeFunctionCreateRequest(
		@NotBlank(message = "Function module is required")
		@Size(max = 50, message = "Function module must be at most 50 characters")
		String functionModule,

		@NotBlank(message = "Function code is required")
		@Size(max = 50, message = "Function code must be at most 50 characters")
		String functionCode,

		@NotBlank(message = "Function name is required")
		@Size(max = 100, message = "Function name must be at most 100 characters")
		String functionName,

		Boolean activeFlag) {
}
