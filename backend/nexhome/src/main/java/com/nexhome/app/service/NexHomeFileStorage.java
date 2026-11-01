package com.nexhome.app.service;

import java.time.Duration;

/**
 * Where uploaded files' bytes are kept: {@link NexHomeLocalFileStorage} (a folder, the default) or
 * {@link NexHomeS3FileStorage} (AWS S3), chosen by {@code nexhome.storage.provider}. Keys are made by
 * {@code NexHomeFiles} ({@code partner-documents/2026/11/<uuid>.pdf}). Failures are {@code ServiceUnavailableException}
 * (503) with a client-safe message; a missing object is {@code ResourceNotFoundException}.
 */
public interface NexHomeFileStorage {

	/** LOCAL or S3, kept with each file. */
	String providerName();

	/** The bucket, or null for local storage. */
	String bucketName();

	void put(String key, byte[] data, String contentType);

	byte[] get(String key);

	/** Removes the object; a missing one is fine. */
	void delete(String key);

	/**
	 * A link that downloads the object straight from storage for {@code validFor}, opening as {@code fileName} of
	 * {@code contentType}; null when the storage cannot sign links (the application then signs its own).
	 */
	String signedUrl(String key, Duration validFor, String fileName, String contentType);
}
