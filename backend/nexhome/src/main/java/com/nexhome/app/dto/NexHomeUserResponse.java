package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexhome.app.model.NexHomeUserStatus;

public record NexHomeUserResponse(
		Long userId,
		String phone,
		String email,
		String roleName,
		NexHomeUserStatus userStatus,
		boolean activeFlag,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
