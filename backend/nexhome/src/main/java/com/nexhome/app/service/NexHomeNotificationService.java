package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomeNotificationDeliveryLogResponse;
import com.nexhome.app.dto.NexHomeNotificationReadRequest;
import com.nexhome.app.dto.NexHomeNotificationResponse;
import com.nexhome.app.dto.NexHomeNotificationSendRequest;
import com.nexhome.app.dto.NexHomeNotificationSendResponse;
import com.nexhome.app.dto.NexHomeNotificationStatsResponse;
import com.nexhome.app.dto.NexHomeNotificationSummaryResponse;
import com.nexhome.app.dto.NexHomePageResponse;

/**
 * Module 9: in-app notifications. Every signed-in user reads and clears their own; staff with NOTIFICATION_MANAGE send
 * them and see every notification and delivery log. Other services notify through {@link NexHomeNotifier}.
 */
public interface NexHomeNotificationService {

	/** The caller's notifications, newest first; {@code unread=true}: only unread. */
	NexHomePageResponse<NexHomeNotificationResponse> getMyNotifications(Integer page, Integer size, Boolean unread,
			String notificationType);

	NexHomeNotificationSummaryResponse getMySummary();

	/** Marks the given notifications of the caller read (others' are ignored); returns how many changed. */
	int markRead(NexHomeNotificationReadRequest request);

	int markAllRead();

	/** One of the caller's notifications (404 for anyone else's). */
	void deleteMine(Long notificationId);

	/** Every notification of the caller; returns how many. */
	int clearMine();

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
}
