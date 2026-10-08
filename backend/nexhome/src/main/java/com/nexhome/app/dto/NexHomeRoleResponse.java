package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeRoleResponse(
		Long roleId,
		String roleName,
		String description,
		boolean activeFlag,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en", timezone = "Asia/Kolkata")
		Instant creationDate) {
}
