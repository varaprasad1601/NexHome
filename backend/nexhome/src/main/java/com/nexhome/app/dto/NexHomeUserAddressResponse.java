package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record NexHomeUserAddressResponse(
		Long userAddressId,
		Long userId,
		String address1,
		String address2,
		String address3,
		String city,
		String district,
		String state,
		String pinCode,
		boolean activeFlag,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en", timezone = "Asia/Kolkata")
		Instant creationDate) {
}
