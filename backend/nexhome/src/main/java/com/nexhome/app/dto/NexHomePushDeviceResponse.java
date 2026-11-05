package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/** A registered phone (the token itself is not returned). */
public record NexHomePushDeviceResponse(
		Long pushDeviceId,
		Long userId,
		String app,
		String platform,
		boolean isActive,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant lastSeenAt) {
}
