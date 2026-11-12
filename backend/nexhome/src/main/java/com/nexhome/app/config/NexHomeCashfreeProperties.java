package com.nexhome.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cashfree Payment Gateway ({@code nexhome.payments.cashfree.*}; used with {@code nexhome.payments.provider=cashfree}).
 *
 * @param appId the App ID ({@code CASHFREE_APP_ID}), sent as x-client-id
 * @param secretKey the Secret Key ({@code CASHFREE_SECRET_KEY}), sent as x-client-secret and the webhook signing key;
 *        never logged
 * @param environment sandbox (default) or production ({@code CASHFREE_ENV})
 * @param apiVersion the x-api-version header (default 2025-01-01)
 * @param notifyUrl optional public https address of {@code /api/payments/webhooks/CASHFREE}
 *        ({@code CASHFREE_NOTIFY_URL}); without it payments are confirmed when the app checks the order
 * @param timeoutSeconds connect and read timeout of each call (default 15)
 */
@ConfigurationProperties("nexhome.payments.cashfree")
public record NexHomeCashfreeProperties(
		String appId,
		String secretKey,
		String environment,
		String apiVersion,
		String notifyUrl,
		Integer timeoutSeconds) {

	public boolean production() {
		return "production".equalsIgnoreCase(environment == null ? "" : environment.trim());
	}

	public String baseUrl() {
		return production() ? "https://api.cashfree.com/pg" : "https://sandbox.cashfree.com/pg";
	}

	public String version() {
		return apiVersion == null || apiVersion.isBlank() ? "2025-01-01" : apiVersion.trim();
	}

	public int timeout() {
		return timeoutSeconds == null || timeoutSeconds <= 0 ? 15 : timeoutSeconds;
	}
}
