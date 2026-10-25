package com.nexhome.app.config;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.nexhome.app.repository.NexHomeAuthRefreshTokenRepository;

import lombok.RequiredArgsConstructor;

/**
 * Accepts an access token only while its login session is open. A JWT is otherwise valid until it expires, so without
 * this check logging out (or being blocked) would leave the access token working for up to its full lifetime.
 * <p>
 * The session is open while it has an unrevoked, unexpired refresh token; logout, logout-all, refresh-token reuse
 * and leaving the ACTIVE status revoke it. One indexed lookup per authenticated request.
 */
@Component
@RequiredArgsConstructor
public class NexHomeSessionTokenValidator implements OAuth2TokenValidator<Jwt> {

	/** Access token claim holding the login session id ({@code nexhome_auth_refresh_tokens.session_id}). */
	public static final String SESSION_ID_CLAIM = "sid";

	/** Description of the rejection; NexHomeSecurityErrorHandler turns it into the 401 message. */
	public static final String SESSION_ENDED = "The session has ended. Log in again.";

	private final NexHomeAuthRefreshTokenRepository refreshTokenRepository;

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		UUID sessionId;
		try {
			sessionId = UUID.fromString(jwt.getClaimAsString(SESSION_ID_CLAIM));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			// Tokens without a session (e.g. issued before sessions existed) are not accepted
			return failure();
		}
		return refreshTokenRepository.existsBySessionIdAndRevokedAtIsNullAndExpiresAtAfter(sessionId, Instant.now())
				? OAuth2TokenValidatorResult.success()
				: failure();
	}

	private static OAuth2TokenValidatorResult failure() {
		return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, SESSION_ENDED, null));
	}
}
