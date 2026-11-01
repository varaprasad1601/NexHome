package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Attaches a file to a ticket (or one of its messages): its requester or staff with SUPPORT_MANAGE write. The file is
 * uploaded first ({@code POST /api/files/upload}, purpose SUPPORT_ATTACHMENT: PDF, JPEG, PNG or WebP up to 10 MB);
 * at most 5 per message (or on the ticket itself).
 */
public record NexHomeSupportAttachmentCreateRequest(
		@NotNull(message = "Support ticket ID is required")
		Long supportTicketId,

		Long messageId,

		@NotNull(message = "File ID is required")
		Long fileId) {
}
