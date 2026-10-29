package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A support ticket. Requester and assignee names and roles are as audit entries show them (staff by their staff
 * profile, others by their partner or customer profile, else the mobile number).
 */
public record NexHomeSupportTicketResponse(
		Long supportTicketId,
		String publicId,
		Long requesterUserId,
		String requesterName,
		String requesterRole,
		String requesterMobile,
		Long bookingId,
		String bookingPublicId,
		Long paymentId,
		String paymentReference,
		String category,
		String subject,
		String description,
		String priority,
		String status,
		Long assignedToUserId,
		String assignedToName,
		String assignedToRole,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant resolvedAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant closedAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate,
		Long createdBy,
		String createdByName,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant updatedDate,
		Long updatedBy) {
}
