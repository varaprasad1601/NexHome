package com.nexhome.app.exception;

/** 503: an outside service the request needs (e.g. the SMS provider) is not working; the client may try again. */
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String message) {
		super(message);
	}
}
