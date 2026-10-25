package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeRoleFunctionResponse(
		Long roleFunctionId,
		Short roleId,
		String roleCode,
		Long functionId,
		String functionCode,
		String functionName,
		boolean canRead,
		boolean canWrite,
		boolean canDelete,
		boolean activeFlag,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
