package com.nexhome.app.dto;

import java.util.List;

/** A ticket with its messages (oldest first; internal notes only for staff) and attachments. */
public record NexHomeSupportTicketDetailResponse(
		NexHomeSupportTicketResponse ticket,
		List<NexHomeSupportMessageResponse> messages,
		List<NexHomeSupportAttachmentResponse> attachments) {
}
