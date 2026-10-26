package com.nexhome.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeAddressResponse(
		Long addressId,
		String publicId,
		Long userId,
		String contactName,
		String mobileNumber,
		String addressType,
		String label,
		String addressLine1,
		String addressLine2,
		String landmark,
		String city,
		String state,
		String postalCode,
		String countryCode,
		BigDecimal latitude,
		BigDecimal longitude,
		boolean isDefault,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant creationDate) {
}
