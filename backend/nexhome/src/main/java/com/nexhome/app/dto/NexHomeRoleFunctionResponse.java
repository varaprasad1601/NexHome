package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeRoleFunctionResponse(
		Long roleFunctionId,
		Long roleId,
		String roleName,
		Long functionId,
		String functionCode,
		String functionName,
		boolean read,
		boolean write,
		boolean delete,
		boolean activeFlag,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en", timezone = "Asia/Kolkata")
		Instant creationDate) {
}
