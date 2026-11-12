package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One status change, with who made it (name and role, as the audit trails).
 */
public record NexHomeBookingHistoryResponse(
		Long historyId,
		String oldStatus,
		String newStatus,
		Long changedBy,
		String changedByName,
		String changedByRole,
		/**
		 * Who it was for this booking: CUSTOMER (its customer), PARTNER (a partner it was offered or assigned to),
		 * STAFF (NexHome staff) or SYSTEM (NexHome itself: payments, scheduled jobs); null when unknown. The apps show
		 * "by Admin / Partner / Customer" and nothing for the viewer's own actions.
		 */
		String changedByParty,
		String reason,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant changedAt) {
}
