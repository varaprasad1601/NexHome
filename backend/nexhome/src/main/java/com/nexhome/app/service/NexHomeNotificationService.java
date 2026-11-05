package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomeNotificationDeliveryLogResponse;
import com.nexhome.app.dto.NexHomeNotificationReadRequest;
import com.nexhome.app.dto.NexHomeNotificationResponse;
import com.nexhome.app.dto.NexHomeNotificationSendRequest;
import com.nexhome.app.dto.NexHomeNotificationSendResponse;
import com.nexhome.app.dto.NexHomeNotificationStatsResponse;
import com.nexhome.app.dto.NexHomeNotificationSummaryResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePushDeviceRegisterRequest;
import com.nexhome.app.dto.NexHomePushDeviceResponse;
import com.nexhome.app.dto.NexHomePushDeviceUnregisterRequest;

/**
 * Module 9: in-app notifications. Every signed-in user reads and clears their own; staff with NOTIFICATION_MANAGE send
 * them and see every notification and delivery log. Other services notify through {@link NexHomeNotifier}.
 */
public interface NexHomeNotificationService {

	/**
	 * The caller's notifications, newest first; {@code unread=true}: only unread. {@code app} (CUSTOMER, PARTNER,
	 * STAFF; V54): only that app's and those for every app; missing: all of them.
	 */
	NexHomePageResponse<NexHomeNotificationResponse> getMyNotifications(Integer page, Integer size, Boolean unread,
			String notificationType, String app);

	/** Counts of the caller's notifications, by {@code app} as above. */
	NexHomeNotificationSummaryResponse getMySummary(String app);

	/** Marks the given notifications of the caller read (others' are ignored); returns how many changed. */
	int markRead(NexHomeNotificationReadRequest request);

	/** The caller's notifications of {@code app} (as above) read; returns how many changed. */
	int markAllRead(String app);

	/** One of the caller's notifications (404 for anyone else's). */
	void deleteMine(Long notificationId);

	/** Every notification of the caller of {@code app} (as above); returns how many. */
	int clearMine(String app);

	/** NOTIFICATION_MANAGE write. */
	NexHomeNotificationSendResponse send(NexHomeNotificationSendRequest request);

	/** NOTIFICATION_MANAGE read: every notification, only the filters given. */
	NexHomePageResponse<NexHomeNotificationResponse> getNotifications(Integer page, Integer size, Long userId,
			String notificationType, Boolean unread, String search);

	/** NOTIFICATION_MANAGE read. */
	NexHomeNotificationStatsResponse getStats();

	/** NOTIFICATION_MANAGE read. */
	NexHomePageResponse<NexHomeNotificationDeliveryLogResponse> getDeliveryLogs(Integer page, Integer size,
			String channel, String deliveryStatus, Long userId);

	/**
	 * Registers the caller's phone for pushes (V53): a new token is added, a known one refreshed, reactivated and, if
	 * another account used it on that phone before, moved to the caller.
	 */
	NexHomePushDeviceResponse registerDevice(NexHomePushDeviceRegisterRequest request);

	/** Stops pushes to one of the caller's phones (LOGGED_OUT); an unknown or another user's token is ignored. */
	void unregisterDevice(NexHomePushDeviceUnregisterRequest request);
}
