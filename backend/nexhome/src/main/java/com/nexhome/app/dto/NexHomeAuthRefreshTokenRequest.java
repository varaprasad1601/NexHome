package com.nexhome.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A refresh token presented to get new tokens or to log out.
 */
public record NexHomeAuthRefreshTokenRequest(
		@NotBlank(message = "Refresh token is required")
		@Size(max = 200, message = "Refresh token must be at most 200 characters")
		String refreshToken) {

	/** Never print the token, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "NexHomeAuthRefreshTokenRequest[refreshToken=****]";
	}
}
