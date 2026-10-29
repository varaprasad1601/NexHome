package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A file attached to a ticket (or one of its messages): its requester or staff with SUPPORT_MANAGE write. Only the
 * storage key is kept; there is no file storage yet.
 */
public record NexHomeSupportAttachmentCreateRequest(
		@NotNull(message = "Support ticket ID is required")
		Long supportTicketId,
		Long messageId,
		@NotBlank(message = "Storage key is required")
		@Size(max = 1000, message = "Storage key must be at most 1000 characters")
		String storageKey,
		@NotBlank(message = "File type is required")
		@Size(max = 100, message = "File type must be at most 100 characters")
		String fileType) {
}
