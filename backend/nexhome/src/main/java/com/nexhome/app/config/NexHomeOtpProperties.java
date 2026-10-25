package com.nexhome.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OTP settings ({@code nexhome.otp.*} in application.properties).
 *
 * @param length number of digits in a generated OTP
 * @param expiryMinutes how long an OTP can be verified
 * @param maxAttempts wrong attempts after which the OTP is LOCKED
 * @param resendCooldownSeconds minimum time between two OTPs for the same mobile number and purpose
 * @param logPlaintext log generated OTPs; only for local development, where no SMS provider is configured
 */
@ConfigurationProperties("nexhome.otp")
public record NexHomeOtpProperties(
		int length,
		int expiryMinutes,
		int maxAttempts,
		int resendCooldownSeconds,
		boolean logPlaintext) {
}
