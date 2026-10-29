package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One entry of a service's activity log, from the audit columns of the service and its variants, images and
 * requirements (each row keeps its creation and its last update only).
 *
 * @param action CREATED, UPDATED or ADDED
 * @param subject what it happened to, e.g. "Service", "Variant 2 BHK", "Image sofa.jpg"
 * @param userName the staff member's name from their admin profile, else their mobile number; null when unknown
 */
public record NexHomeServiceActivityResponse(
		String action,
		String subject,
		Long userId,
		String userName,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant at) {
}
