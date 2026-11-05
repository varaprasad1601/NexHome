package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Stops pushes to one of the signed-in user's phones (sent by the apps before logging out). */
public record NexHomePushDeviceUnregisterRequest(
		@NotBlank(message = "Device token is required")
		@Size(max = 512, message = "Device token must be at most 512 characters")
		String deviceToken) {

	@Override
	public String toString() {
		return "NexHomePushDeviceUnregisterRequest[deviceToken=****]";
	}
}
