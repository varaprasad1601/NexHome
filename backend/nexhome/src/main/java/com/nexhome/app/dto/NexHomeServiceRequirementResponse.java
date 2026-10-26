package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeServiceRequirementResponse(
		Long serviceRequirementId,

		Long serviceId,

		String requirementType,

		String description,

		boolean isMandatory,

		int displayOrder,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
