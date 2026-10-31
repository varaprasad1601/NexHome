package com.nexhome.app.service;

/**
 * Sends text messages (OTPs for now). The implementation is chosen by {@code nexhome.sms.provider}: MSG91
 * ({@link NexHomeMsg91SmsSender}) or none ({@link NexHomeLogSmsSender}).
 */
public interface NexHomeSmsSender {

	/** The provider's name as delivery logs record it, e.g. MSG91; null when nothing is sent. */
	String providerName();

	/**
	 * Sends the OTP to the number. Returns the provider's message (request) id, or null when nothing was sent.
	 *
	 * @throws NexHomeSmsException when the provider cannot be reached or refuses the message
	 */
	String sendOtp(String countryCode, String mobileNumber, String otp);

	/** The number as providers take it: the country code's digits followed by the number, e.g. 919876543210. */
	static String internationalNumber(String countryCode, String mobileNumber) {
		return countryCode.replaceAll("\\D", "") + mobileNumber.replaceAll("\\D", "");
	}

	/** The provider could not send the message (no OTP, key or number in the message). */
	class NexHomeSmsException extends RuntimeException {

		public NexHomeSmsException(String message) {
			super(message);
		}

		public NexHomeSmsException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
