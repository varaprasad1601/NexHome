package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Staff (SUPPORT_MANAGE write) change a ticket; a null field keeps its value. {@code status}: SUPPORT_TICKET_STATUS
 * (a CLOSED ticket can only be reopened, to IN_PROGRESS). {@code assignedToUserId}: the staff member handling it
 * (must be able to read support tickets; assigning an OPEN ticket makes it ASSIGNED); {@code unassign: true} takes
 * them off (an ASSIGNED ticket goes back to OPEN).
 */
public record NexHomeSupportTicketUpdateRequest(
		@NotNull(message = "Support ticket ID is required")
		Long supportTicketId,
		String category,
		String priority,
		String status,
		Long assignedToUserId,
		Boolean unassign) {
}
