package com.nexhome.app.exception;

/**
 * A referenced record (user, role, ...) does not exist. Mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}
}
