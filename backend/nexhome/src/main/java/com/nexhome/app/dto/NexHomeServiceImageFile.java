package com.nexhome.app.dto;

/**
 * A service image's bytes, for the image endpoint.
 */
public record NexHomeServiceImageFile(
		String contentType,
		byte[] data) {
}
