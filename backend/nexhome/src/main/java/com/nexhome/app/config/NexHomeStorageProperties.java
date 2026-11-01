package com.nexhome.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * File storage settings ({@code nexhome.storage.*}). {@code provider} is {@code local} (the default: files in a
 * folder on this machine), {@code s3} (a private bucket on AWS S3, which needs the bucket and region) or {@code r2}
 * (a private Cloudflare R2 bucket, which needs its endpoint, bucket and API token keys); startup fails without them.
 *
 * @param provider local, s3 or r2 ({@code STORAGE_PROVIDER})
 * @param local the local folder
 * @param s3 the S3 bucket and its credentials
 * @param r2 the Cloudflare R2 bucket and its credentials
 * @param downloadUrlMinutes how long a download link of a private file works (default 5)
 * @param unattachedHours uploads not attached to anything after this many hours are removed (default 24)
 */
@ConfigurationProperties("nexhome.storage")
public record NexHomeStorageProperties(
		String provider,
		Local local,
		S3 s3,
		R2 r2,
		int downloadUrlMinutes,
		int unattachedHours) {

	public static final String LOCAL = "local";
	public static final String S3 = "s3";
	public static final String R2 = "r2";

	public NexHomeStorageProperties {
		provider = provider == null || provider.isBlank() ? LOCAL : provider.trim().toLowerCase();
		if (!LOCAL.equals(provider) && !S3.equals(provider) && !R2.equals(provider)) {
			throw new IllegalStateException("nexhome.storage.provider must be local, s3 or r2, not " + provider + ".");
		}
		if (R2.equals(provider) && (r2 == null || blank(r2.endpoint()) || blank(r2.bucket()) || blank(r2.accessKey())
				|| blank(r2.secretKey()))) {
			throw new IllegalStateException(
					"nexhome.storage.provider is r2, so R2_ENDPOINT, R2_BUCKET, R2_ACCESS_KEY and R2_SECRET_KEY must be set.");
		}
		if (S3.equals(provider) && (s3 == null || blank(s3.bucket()) || blank(s3.region()))) {
			throw new IllegalStateException("nexhome.storage.provider is s3, so S3_BUCKET and S3_REGION must be set.");
		}
		if (LOCAL.equals(provider) && (local == null || blank(local.root()))) {
			throw new IllegalStateException("nexhome.storage.local.root must be set for local file storage.");
		}
		downloadUrlMinutes = downloadUrlMinutes <= 0 ? 5 : downloadUrlMinutes;
		unattachedHours = unattachedHours <= 0 ? 24 : unattachedHours;
	}

	/** @param root the folder that holds the files ({@code STORAGE_LOCAL_ROOT}) */
	public record Local(String root) {
	}

	/**
	 * A private S3 bucket.
	 *
	 * @param bucket the bucket's name ({@code S3_BUCKET})
	 * @param region its region ({@code S3_REGION}, default ap-south-1: Mumbai)
	 * @param accessKey the access key id ({@code S3_ACCESS_KEY}); with either key empty, the AWS default credentials
	 *        are used (environment, profile or the server's IAM role)
	 * @param secretKey the secret access key ({@code S3_SECRET_KEY}); never logged
	 * @param endpoint another S3-compatible endpoint (MinIO, R2); empty for AWS
	 */
	public record S3(
			String bucket,
			String region,
			String accessKey,
			String secretKey,
			String endpoint) {

		/** Never print the keys. */
		@Override
		public String toString() {
			return "S3[bucket=" + bucket + ", region=" + region + ", endpoint=" + endpoint + "]";
		}
	}

	/**
	 * A private Cloudflare R2 bucket, reached through R2's S3-compatible API.
	 *
	 * @param endpoint the account's S3 API address, {@code https://<account id>.r2.cloudflarestorage.com}
	 *        ({@code R2_ENDPOINT}; a trailing {@code /<bucket>} is dropped)
	 * @param bucket the bucket's name ({@code R2_BUCKET})
	 * @param accessKey the R2 API token's access key id ({@code R2_ACCESS_KEY})
	 * @param secretKey the R2 API token's secret access key ({@code R2_SECRET_KEY}); never logged
	 */
	public record R2(
			String endpoint,
			String bucket,
			String accessKey,
			String secretKey) {

		public R2 {
			bucket = bucket == null ? null : bucket.trim();
			if (endpoint != null) {
				endpoint = endpoint.trim().replaceAll("/+$", "");
				if (bucket != null && !bucket.isEmpty() && endpoint.endsWith("/" + bucket)) {
					endpoint = endpoint.substring(0, endpoint.length() - bucket.length() - 1);
				}
			}
		}

		/** Never print the keys. */
		@Override
		public String toString() {
			return "R2[bucket=" + bucket + ", endpoint=" + endpoint + "]";
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
