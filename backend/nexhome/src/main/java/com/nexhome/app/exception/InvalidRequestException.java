package com.nexhome.app.exception;

/**
 * The request is well-formed but cannot be processed, e.g. it references a missing record. Mapped to HTTP 400.
 */
public class InvalidRequestException extends RuntimeException {

	public InvalidRequestException(String message) {
		super(message);
	}
}
