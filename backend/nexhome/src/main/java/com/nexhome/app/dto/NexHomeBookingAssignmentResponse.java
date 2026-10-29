package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A partner's assignment to a booking. {@code assignedByName} / {@code assignedByRole}: who offered or assigned it
 * (filled in the booking detail, null elsewhere).
 */
public record NexHomeBookingAssignmentResponse(
		Long assignmentId,
		Long bookingId,
		String bookingPublicId,
		Long partnerProfileId,
		String partnerPublicId,
		String partnerName,
		Long assignedBy,
		String assignmentStatus,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant offeredAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant respondedAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant expiresAt,
		String rejectionReason,
		String assignedByName,
		String assignedByRole) {

	/** The same assignment with who assigned it. */
	public NexHomeBookingAssignmentResponse withAssignedBy(String name, String role) {
		return new NexHomeBookingAssignmentResponse(assignmentId, bookingId, bookingPublicId, partnerProfileId,
				partnerPublicId, partnerName, assignedBy, assignmentStatus, offeredAt, respondedAt, expiresAt,
				rejectionReason, name, role);
	}
}
