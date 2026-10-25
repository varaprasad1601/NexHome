package com.nexhome.app.exception;

/**
 * The client must wait before repeating the request, e.g. before requesting another OTP. Mapped to HTTP 429.
 */
public class TooManyRequestsException extends RuntimeException {

	public TooManyRequestsException(String message) {
		super(message);
	}
}
