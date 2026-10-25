package com.nexhome.app.exception;

/**
 * The request would create a record that already exists. Mapped to HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {

	public DuplicateResourceException(String message) {
		super(message);
	}
}
