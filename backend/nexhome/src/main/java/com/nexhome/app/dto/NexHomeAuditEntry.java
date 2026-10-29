package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One line of an audit trail or activity log, read from the audit columns (creation_date / created_by, updated_date /
 * updated_by, verified_by / verified_at, ...) of a record. {@code event} says what happened, e.g. PROFILE_CREATED,
 * DOCUMENT_VERIFIED, ADDRESS_ADDED; {@code subject} names the record when it is not the profile itself.
 * {@code byName} is the user's staff, partner or customer profile name, else their mobile number ("System" for user
 * 1), and {@code byRole} the name of their highest active role. {@code at}, {@code by}, {@code byName} and
 * {@code byRole} are null for what has not happened yet.
 */
public record NexHomeAuditEntry(
		String event,
		String subject,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant at,
		Long by,
		String byName,
		String byRole) {
}
