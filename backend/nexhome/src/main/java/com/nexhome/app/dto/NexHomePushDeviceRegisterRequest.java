package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registers the signed-in user's phone for push notifications: the app install's Firebase Cloud Messaging token,
 * which app (CUSTOMER or PARTNER) and the platform (ANDROID or IOS). Sent after every sign-in and app start; the same
 * token again only refreshes it.
 */
public record NexHomePushDeviceRegisterRequest(
		@NotBlank(message = "Device token is required")
		@Size(max = 512, message = "Device token must be at most 512 characters")
		String deviceToken,
		@NotBlank(message = "App is required")
		@Pattern(regexp = "^(CUSTOMER|PARTNER)$", message = "App must be CUSTOMER or PARTNER")
		String app,
		@NotBlank(message = "Platform is required")
		@Pattern(regexp = "^(ANDROID|IOS)$", message = "Platform must be ANDROID or IOS")
		String platform) {

	/** The token is a credential for reaching the phone: never printed in full. */
	@Override
	public String toString() {
		return "NexHomePushDeviceRegisterRequest[app=" + app + ", platform=" + platform + ", deviceToken=****]";
	}
}
