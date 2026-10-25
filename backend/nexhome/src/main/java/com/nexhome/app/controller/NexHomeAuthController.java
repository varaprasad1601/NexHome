package com.nexhome.app.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeAuthOtpCreateRequest;
import com.nexhome.app.dto.NexHomeAuthOtpResponse;
import com.nexhome.app.dto.NexHomeAuthOtpVerifyRequest;
import com.nexhome.app.dto.NexHomeAuthPhoneChangeRequest;
import com.nexhome.app.dto.NexHomeAuthPhoneChangeVerifyRequest;
import com.nexhome.app.dto.NexHomeAuthRefreshTokenRequest;
import com.nexhome.app.dto.NexHomeAuthRegisterRequest;
import com.nexhome.app.dto.NexHomeAuthTokenResponse;
import com.nexhome.app.service.NexHomeAuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Sign-up and login. register, otp/request, otp/verify, token/refresh and logout are public (NexHomeSecurityConfig);
 * logout/all and the phone change endpoints need an access token.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class NexHomeAuthController {

	private final NexHomeAuthService authService;

	/** Customer or partner sign-up; sends a REGISTRATION OTP. */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeAuthOtpResponse register(@Valid @RequestBody NexHomeAuthRegisterRequest request) {
		return authService.register(request);
	}

	/** Sends a LOGIN or REGISTRATION OTP. */
	@PostMapping("/otp/request")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeAuthOtpResponse requestOtp(@Valid @RequestBody NexHomeAuthOtpCreateRequest request) {
		return authService.requestOtp(request);
	}

	/** Verifies a LOGIN or REGISTRATION OTP and returns the user with new tokens. */
	@PostMapping("/otp/verify")
	public NexHomeAuthOtpResponse verifyOtp(@Valid @RequestBody NexHomeAuthOtpVerifyRequest request,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		return authService.verifyOtp(request, userAgent);
	}

	@PostMapping("/token/refresh")
	public NexHomeAuthTokenResponse refreshTokens(@Valid @RequestBody NexHomeAuthRefreshTokenRequest request,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		return authService.refreshTokens(request, userAgent);
	}

	/** Ends one session; the client should also discard its access token. */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody NexHomeAuthRefreshTokenRequest request) {
		authService.logout(request);
	}

	/** Ends every session of the signed-in user, e.g. after losing a phone. */
	@PostMapping("/logout/all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logoutAll() {
		authService.logoutAll();
	}

	@PostMapping("/phone-change/otp/request")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeAuthOtpResponse requestPhoneChangeOtp(@Valid @RequestBody NexHomeAuthPhoneChangeRequest request) {
		return authService.requestPhoneChangeOtp(request);
	}

	@PostMapping("/phone-change/otp/verify")
	public NexHomeAuthOtpResponse verifyPhoneChangeOtp(
			@Valid @RequestBody NexHomeAuthPhoneChangeVerifyRequest request) {
		return authService.verifyPhoneChangeOtp(request);
	}
}
