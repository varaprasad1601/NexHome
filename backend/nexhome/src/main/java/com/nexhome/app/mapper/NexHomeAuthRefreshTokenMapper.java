package com.nexhome.app.mapper;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nexhome.app.model.NexHomeAuthRefreshToken;
import com.nexhome.app.model.NexHomeUser;

@Component
public class NexHomeAuthRefreshTokenMapper {

	/** A new, unrevoked session; the user is the acting user, since they are the one signing in. */
	public NexHomeAuthRefreshToken toRefreshTokenEntity(NexHomeUser user, UUID sessionId, String tokenHash,
			Instant expiresAt, String userAgent) {
		NexHomeAuthRefreshToken refreshToken = new NexHomeAuthRefreshToken();
		refreshToken.setUser(user);
		refreshToken.setSessionId(sessionId);
		refreshToken.setTokenHash(tokenHash);
		refreshToken.setExpiresAt(expiresAt);
		refreshToken.setUserAgent(userAgent == null || userAgent.isBlank() ? null
				: userAgent.substring(0, Math.min(userAgent.length(), 500)));
		refreshToken.setCreatedBy(user.getId());
		refreshToken.setUpdatedBy(user.getId());
		return refreshToken;
	}
}
