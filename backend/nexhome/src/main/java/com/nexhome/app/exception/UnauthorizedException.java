package com.nexhome.app.exception;

/**
 * The caller is not authenticated, or presented an invalid, expired or revoked token. Mapped to HTTP 401.
 */
public class UnauthorizedException extends RuntimeException {

	public UnauthorizedException(String message) {
		super(message);
	}
}
