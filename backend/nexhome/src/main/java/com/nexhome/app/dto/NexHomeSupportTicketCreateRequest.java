package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Opens a support ticket. {@code requesterId}: whose ticket it is; missing means the caller's own, anyone else's
 * needs SUPPORT_MANAGE write. The booking (and the payment, which must be of that booking) must be the requester's
 * unless staff open it. {@code category}: SUPPORT_CATEGORY; {@code priority}: SUPPORT_PRIORITY (default MEDIUM).
 */
public record NexHomeSupportTicketCreateRequest(
		Long requesterId,
		Long bookingId,
		Long paymentId,
		@NotBlank(message = "Category is required")
		String category,
		@NotBlank(message = "Subject is required")
		@Size(max = 200, message = "Subject must be at most 200 characters")
		String subject,
		@NotBlank(message = "Description is required")
		@Size(max = 5000, message = "Description must be at most 5000 characters")
		String description,
		String priority) {
}
