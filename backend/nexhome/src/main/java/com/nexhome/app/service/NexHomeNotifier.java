package com.nexhome.app.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.nexhome.app.model.NexHomeLookupCodes.DeliveryStatus;
import com.nexhome.app.model.NexHomeLookupCodes.NotificationChannel;
import com.nexhome.app.model.NexHomeNotification;
import com.nexhome.app.model.NexHomeNotificationDeliveryLog;
import com.nexhome.app.repository.NexHomeNotificationDeliveryLogRepository;
import com.nexhome.app.repository.NexHomeNotificationRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Sends in-app notifications for the other services (Module 9), inside the caller's transaction: one notification
 * per user and an IN_APP / DELIVERED delivery log (no push, SMS or email provider exists yet). Package-private like
 * {@link NexHomeSettlement}; the endpoints are {@link NexHomeNotificationService}'s. Never sends to the system account
 * (user 1) or to the user who caused the event.
 */
@Component
@RequiredArgsConstructor
class NexHomeNotifier {

	/** What a notification is about ({@code reference_type}); the id is that record's id. */
	static final String BOOKING = "BOOKING";
	static final String SUPPORT_TICKET = "SUPPORT_TICKET";
	static final String REVIEW = "REVIEW";
	static final String PARTNER_PROFILE = "PARTNER_PROFILE";

	private static final Long SYSTEM_USER_ID = 1L;

	private final NexHomeNotificationRepository notificationRepository;
	private final NexHomeNotificationDeliveryLogRepository deliveryLogRepository;
	private final NexHomeUserRepository userRepository;
	private final NexHomeUserRoleRepository userRoleRepository;

	/** One notification. {@code referenceType} / {@code referenceId}: what it is about, both or neither. */
	record Message(String type, String title, String message, String referenceType, Long referenceId) {
	}

	/** Notifies one user, unless it is the actor or the system account. */
	void notify(Long userId, Message message, Long actingUserId) {
		notifyAll(userId == null ? List.of() : List.of(userId), message, actingUserId);
	}

	/** Notifies the staff who may read {@code functionCode} (see {@code findStaffWhoCanRead}), but not the actor. */
	void notifyStaff(String functionCode, Message message, Long actingUserId) {
		notifyAll(userRoleRepository.findStaffWhoCanRead(functionCode), message, actingUserId);
	}

	/** Notifies each user once; returns the notifications stored. */
	List<NexHomeNotification> notifyAll(Collection<Long> userIds, Message message, Long actingUserId) {
		Set<Long> recipients = new LinkedHashSet<>(userIds);
		recipients.removeIf(id -> id == null || SYSTEM_USER_ID.equals(id) || Objects.equals(id, actingUserId));
		if (recipients.isEmpty()) {
			return List.of();
		}
		Instant now = Instant.now();
		List<NexHomeNotification> notifications = new ArrayList<>();
		for (Long userId : recipients) {
			NexHomeNotification notification = new NexHomeNotification();
			notification.setUser(userRepository.getReferenceById(userId));
			notification.setNotificationType(message.type());
			notification.setTitle(message.title());
			notification.setMessage(message.message());
			notification.setReferenceType(message.referenceType());
			notification.setReferenceId(message.referenceId());
			notification.setCreatedBy(actingUserId);
			notification.setUpdatedBy(actingUserId);
			notifications.add(notification);
		}
		notificationRepository.saveAll(notifications);
		deliveryLogRepository.saveAll(notifications.stream().map(notification -> {
			NexHomeNotificationDeliveryLog log = new NexHomeNotificationDeliveryLog();
			log.setNotification(notification);
			log.setUser(notification.getUser());
			log.setChannel(NotificationChannel.IN_APP);
			log.setDeliveryStatus(DeliveryStatus.DELIVERED);
			log.setSentAt(now);
			log.setDeliveredAt(now);
			log.setCreatedBy(actingUserId);
			log.setUpdatedBy(actingUserId);
			return log;
		}).toList());
		return notifications;
	}
}
