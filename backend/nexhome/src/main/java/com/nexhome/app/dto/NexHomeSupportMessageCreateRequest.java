package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A reply on a ticket: its requester, or staff with SUPPORT_MANAGE write. {@code isInternalNote} (staff only): a note
 * the requester never sees.
 */
public record NexHomeSupportMessageCreateRequest(
		@NotNull(message = "Support ticket ID is required")
		Long supportTicketId,
		@NotBlank(message = "Message is required")
		@Size(max = 5000, message = "Message must be at most 5000 characters")
		String message,
		Boolean isInternalNote) {
}
