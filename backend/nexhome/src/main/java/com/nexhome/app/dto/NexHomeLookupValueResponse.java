package com.nexhome.app.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeLookupValueResponse(
		Long lookupValueId,
		Long lookupTypeId,
		String typeCode,
		String valueCode,
		String displayName,
		String description,
		int displayOrder,
		boolean activeFlag,
		boolean isDefault,
		Map<String, Object> metadata,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
