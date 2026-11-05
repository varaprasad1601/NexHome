package com.nexhome.app.service;

import java.util.Map;

/**
 * Sends one push notification to one app install (its FCM registration token). Implemented by
 * {@link NexHomeFcmPushSender} ({@code nexhome.push.provider=fcm}) and {@link NexHomeLogPushSender} (the default: nothing
 * is sent).
 */
public interface NexHomePushSender {

	/** False for the log sender: no push is attempted and no delivery log is written. */
	boolean enabled();

	/**
	 * Sends the push and returns the provider's message id.
	 *
	 * @param data string values the app reads when the notification is opened (notificationId, referenceType, ...)
	 * @param high HIGH priority: delivered at once and shown on the high-importance channel
	 * @throws NexHomePushException when it was not sent; {@link NexHomePushException#unregistered()} when the token is
	 *         no longer valid (the app was removed or its data cleared)
	 */
	String send(String deviceToken, String title, String body, Map<String, String> data, boolean high);

	/** A push that was not sent. */
	class NexHomePushException extends RuntimeException {

		private final boolean unregistered;

		public NexHomePushException(String message, boolean unregistered, Throwable cause) {
			super(message, cause);
			this.unregistered = unregistered;
		}

		/** The token is dead: the device should stop receiving pushes. */
		public boolean unregistered() {
			return unregistered;
		}
	}
}
