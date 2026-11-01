package com.nexhome.app.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.nexhome.app.config.NexHomeStorageProperties;

/**
 * Files in a private Cloudflare R2 bucket ({@code nexhome.storage.provider=r2}) through R2's S3-compatible API:
 * region {@code auto}, path-style addresses, objects encrypted at rest by R2 itself, and private files downloaded
 * through presigned links that expire. The bucket needs no public access: public files go through the app too.
 */
@Component
@ConditionalOnProperty(name = "nexhome.storage.provider", havingValue = "r2")
public class NexHomeR2FileStorage extends NexHomeS3FileStorage {

	static final String PROVIDER = "R2";

	public NexHomeR2FileStorage(NexHomeStorageProperties properties) {
		super(PROVIDER, properties.r2().bucket(), "auto", properties.r2().accessKey(), properties.r2().secretKey(),
				properties.r2().endpoint(), false);
	}
}
