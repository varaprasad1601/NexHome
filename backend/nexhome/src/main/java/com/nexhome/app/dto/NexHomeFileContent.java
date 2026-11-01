package com.nexhome.app.dto;

/**
 * A file's bytes, for the endpoints that serve them.
 */
public record NexHomeFileContent(
		String fileName,
		String contentType,
		byte[] data) {
}
