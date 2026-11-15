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
		 * STAFF (NexHome staff) or SYSTEM (NexHome itself: payments, scheduled jobs); null when unknown. From the app
		 * the change came from (V73), so one number that is staff, partner and customer shows the right one; older
		 * rows go by the user (a partner's own answers first). {@code changedByName} / {@code changedByRole} follow it
		 * (the partner's or customer's name with "Partner" / "Customer"). The apps show "by Admin / Partner /
		 * Customer" and nothing for the viewer's own actions.
		 */
		String changedByParty,
		String reason,
		/** What the row stands for when its status alone does not say it: PARTNER_DECLINED (the partner declined the
		 * job, so the booking went back to CONFIRMED), else null. */
		String action,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant changedAt) {
}
