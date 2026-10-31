package com.nexhome.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMS settings ({@code nexhome.sms.*}): who sends the OTPs. {@code provider} is {@code log} (no SMS is sent; the OTP
 * is only logged when {@code nexhome.otp.log-plaintext} is on, i.e. under dev) or {@code msg91}, which needs the
 * MSG91 auth key and the DLT-approved OTP template (startup fails without them).
 */
@ConfigurationProperties("nexhome.sms")
public record NexHomeSmsProperties(
		String provider,
		Msg91 msg91) {

	public static final String LOG = "log";
	public static final String MSG91 = "msg91";

	public NexHomeSmsProperties {
		provider = provider == null || provider.isBlank() ? LOG : provider.trim().toLowerCase();
		if (!LOG.equals(provider) && !MSG91.equals(provider)) {
			throw new IllegalStateException("nexhome.sms.provider must be log or msg91, not " + provider + ".");
		}
		if (MSG91.equals(provider) && (msg91 == null || blank(msg91.authKey()) || blank(msg91.otpTemplateId()))) {
			throw new IllegalStateException(
					"nexhome.sms.provider is msg91, so MSG91_AUTH_KEY and MSG91_OTP_TEMPLATE_ID must be set.");
		}
	}

	/**
	 * MSG91's Flow API.
	 *
	 * @param authKey the account's auth key ({@code MSG91_AUTH_KEY}); never logged
	 * @param otpTemplateId the DLT-approved OTP template's id in MSG91 ({@code MSG91_OTP_TEMPLATE_ID})
	 * @param otpVariable the template's variable that takes the OTP (default {@code otp}: "##otp##" in the template)
	 * @param flowUrl the Flow endpoint
	 * @param timeoutSeconds connect and read timeout
	 */
	public record Msg91(
			String authKey,
			String otpTemplateId,
			String otpVariable,
			String flowUrl,
			int timeoutSeconds) {
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
