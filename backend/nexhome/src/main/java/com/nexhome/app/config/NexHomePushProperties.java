package com.nexhome.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Push notification settings ({@code nexhome.push.*}). {@code provider} is {@code log} (the default: no push is sent;
 * the apps still register their phones) or {@code fcm}: Firebase Cloud Messaging's HTTP v1 API with a service account
 * key ({@code FIREBASE_CREDENTIALS_FILE}, the JSON from Firebase > Project settings > Service accounts > Generate new
 * private key; startup fails without it). The project id comes from the key unless {@code fcm.project-id} is set.
 */
@ConfigurationProperties("nexhome.push")
public record NexHomePushProperties(
		String provider,
		Fcm fcm) {

	public static final String LOG = "log";
	public static final String FCM = "fcm";

	public NexHomePushProperties {
		provider = provider == null || provider.isBlank() ? LOG : provider.trim().toLowerCase();
		if (!LOG.equals(provider) && !FCM.equals(provider)) {
			throw new IllegalStateException("nexhome.push.provider must be log or fcm, not " + provider + ".");
		}
		if (FCM.equals(provider) && (fcm == null || fcm.credentialsFile() == null || fcm.credentialsFile().isBlank())) {
			throw new IllegalStateException(
					"nexhome.push.provider is fcm, so FIREBASE_CREDENTIALS_FILE (the service account key) must be set.");
		}
	}

	/**
	 * Firebase Cloud Messaging.
	 *
	 * @param credentialsFile path of the service account key JSON; never logged, never committed
	 * @param projectId the Firebase project id (empty: the key's {@code project_id})
	 * @param timeoutSeconds connect and read timeout
	 */
	public record Fcm(
			String credentialsFile,
			String projectId,
			int timeoutSeconds) {
	}
}
