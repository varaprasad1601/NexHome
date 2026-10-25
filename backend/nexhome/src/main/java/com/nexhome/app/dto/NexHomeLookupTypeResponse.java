package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeLookupTypeResponse(
		Long lookupTypeId,
		String typeCode,
		String typeName,
		String description,
		boolean activeFlag,
		boolean isSystemManaged,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
