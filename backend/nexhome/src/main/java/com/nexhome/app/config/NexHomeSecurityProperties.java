package com.nexhome.app.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings ({@code nexhome.security.*} in application.properties).
 *
 * @param jwtSecret Base64-encoded HMAC-SHA256 key of at least 32 bytes that signs access tokens; from
 *        {@code JWT_SECRET}, never committed
 * @param jwtIssuer {@code iss} claim of issued tokens; tokens with another issuer are rejected
 * @param accessTokenMinutes lifetime of an access token
 * @param refreshTokenDays lifetime of a refresh token (a login session)
 * @param corsAllowedOrigins browser origins allowed to call the API, e.g. the admin web app; exact origins or
 *        patterns such as {@code http://localhost:[*]}
 * @param dataEncryptionKey Base64-encoded AES-256 key (exactly 32 bytes) that encrypts Aadhaar, document and bank
 *        account numbers; from {@code DATA_ENCRYPTION_KEY}, never committed
 */
@ConfigurationProperties("nexhome.security")
public record NexHomeSecurityProperties(
		String jwtSecret,
		String jwtIssuer,
		int accessTokenMinutes,
		int refreshTokenDays,
		List<String> corsAllowedOrigins,
		String dataEncryptionKey) {
}
