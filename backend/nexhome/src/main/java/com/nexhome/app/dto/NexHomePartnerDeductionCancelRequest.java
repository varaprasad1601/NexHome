package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cancels a deduction recorded by mistake (it no longer counts). */
public record NexHomePartnerDeductionCancelRequest(
		@NotNull(message = "Deduction ID is required")
		Long deductionId,

		@NotBlank(message = "Reason is required")
		@Size(max = 200, message = "Reason must be at most 200 characters")
		String reason) {
}
