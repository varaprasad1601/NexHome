package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomeAuthOtpCreateRequest;
import com.nexhome.app.dto.NexHomeAuthOtpResponse;
import com.nexhome.app.dto.NexHomeAuthOtpVerifyRequest;
import com.nexhome.app.dto.NexHomeAuthPhoneChangeRequest;
import com.nexhome.app.dto.NexHomeAuthPhoneChangeVerifyRequest;
import com.nexhome.app.dto.NexHomeAuthRefreshTokenRequest;
import com.nexhome.app.dto.NexHomeAuthRegisterRequest;
import com.nexhome.app.dto.NexHomeAuthTokenResponse;

/**
 * Sign-up, OTP login, login sessions (access + refresh tokens), logout and phone change.
 */
public interface NexHomeAuthService {

	/** Creates a customer or partner account for a new mobile number and sends it a REGISTRATION OTP. */
	NexHomeAuthOtpResponse register(NexHomeAuthRegisterRequest request);

	/**
	 * Sends a new LOGIN (existing active account) or REGISTRATION (existing account whose number is not verified yet)
	 * OTP. Any pending OTP for the same number and purpose expires.
	 */
	NexHomeAuthOtpResponse requestOtp(NexHomeAuthOtpCreateRequest request);

	/**
	 * Verifies and consumes a LOGIN or REGISTRATION OTP and starts a login session: the response carries the user
	 * and new tokens. A wrong OTP counts as an attempt; too many lock it.
	 *
	 * @param userAgent stored with the session so users can recognise it; may be null
	 */
	NexHomeAuthOtpResponse verifyOtp(NexHomeAuthOtpVerifyRequest request, String userAgent);

	/** Exchanges a refresh token for new tokens. The presented token is used up; reusing it ends every session. */
	NexHomeAuthTokenResponse refreshTokens(NexHomeAuthRefreshTokenRequest request, String userAgent);

	/** Ends the session of the refresh token. Unknown or already revoked tokens are ignored. */
	void logout(NexHomeAuthRefreshTokenRequest request);

	/** Ends every session of the signed-in user. */
	void logoutAll();

	/** Sends a PHONE_CHANGE OTP to the signed-in user's new, unused mobile number. */
	NexHomeAuthOtpResponse requestPhoneChangeOtp(NexHomeAuthPhoneChangeRequest request);

	/** Verifies the PHONE_CHANGE OTP and moves the signed-in user's account to the new number. */
	NexHomeAuthOtpResponse verifyPhoneChangeOtp(NexHomeAuthPhoneChangeVerifyRequest request);
}
