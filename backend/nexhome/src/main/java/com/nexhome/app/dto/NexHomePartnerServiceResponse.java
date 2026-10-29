package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomePartnerServiceResponse(
		Long partnerServiceId,

		Long partnerProfileId,

		Long serviceId,

		String serviceName,

		Long categoryId,

		String categoryName,

		Integer experienceYears,

		boolean isPrimaryService,

		boolean isActive,

		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
