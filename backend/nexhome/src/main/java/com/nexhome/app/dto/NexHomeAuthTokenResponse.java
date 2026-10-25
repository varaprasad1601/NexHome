package com.nexhome.app.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Tokens of a login session. Send the access token as {@code Authorization: Bearer <accessToken>}; when it expires,
 * get new tokens with the refresh token (POST /api/auth/token/refresh). Each refresh token works once.
 *
 * @param tokenType always {@code Bearer}
 */
public record NexHomeAuthTokenResponse(
		String tokenType,
		String accessToken,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant accessTokenExpiresAt,
		String refreshToken,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE, dd MMM yyyy HH:mm:ss z", locale = "en",
				timezone = "Asia/Kolkata")
		Instant refreshTokenExpiresAt) {

	/** Never print the tokens, e.g. when the response is logged. */
	@Override
	public String toString() {
		return "NexHomeAuthTokenResponse[tokenType=" + tokenType + ", accessTokenExpiresAt=" + accessTokenExpiresAt
				+ ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
	}
}
