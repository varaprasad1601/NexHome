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
import com.nexhome.app.repository.NexHomeBookingAssignmentRepository;
import com.nexhome.app.repository.NexHomeBookingRepository;
import com.nexhome.app.repository.NexHomeBookingReviewRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomeNotificationDeliveryLogRepository;
import com.nexhome.app.repository.NexHomeNotificationRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomeSupportTicketRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Sends in-app notifications for the other services (Module 9), inside the caller's transaction: one notification
 * per user and an IN_APP / DELIVERED delivery log, then a push to their phones after the commit
 * ({@link NexHomePushDispatcher}, V53; no SMS or email yet). Package-private like
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
	private final NexHomeSettingsService settingsService;
	private final NexHomePushDispatcher pushDispatcher;
	private final NexHomeBookingRepository bookingRepository;
	private final NexHomeBookingAssignmentRepository assignmentRepository;
	private final NexHomeSupportTicketRepository ticketRepository;
	private final NexHomeBookingReviewRepository reviewRepository;
	private final NexHomePartnerProfileRepository partnerProfileRepository;
	private final NexHomeCustomerProfileRepository customerProfileRepository;

	/** The apps a notification belongs to (V54). */
	static final String ALL = "ALL";
	static final String CUSTOMER = "CUSTOMER";
	static final String PARTNER = "PARTNER";
	static final String STAFF = "STAFF";

	static final String CUSTOMER_PROFILE = "CUSTOMER_PROFILE";
	/** Notification priorities (V45). */
	static final String NORMAL = "NORMAL";
	static final String HIGH = "HIGH";

	/**
	 * One notification. {@code referenceType} / {@code referenceId}: what it is about, both or neither;
	 * {@code priority} NORMAL (the default) or HIGH; {@code audience} the app it belongs to (V54), null to work it out
	 * for each recipient ({@link #audienceOf}).
	 */
	record Message(String type, String title, String message, String referenceType, Long referenceId,
			String priority, String audience) {

		Message(String type, String title, String message, String referenceType, Long referenceId) {
			this(type, title, message, referenceType, referenceId, NORMAL, null);
		}

		/** The same message as HIGH priority. */
		Message high() {
			return new Message(type, title, message, referenceType, referenceId, HIGH, audience);
		}

		/** The same message for one app. */
		Message forApp(String app) {
			return new Message(type, title, message, referenceType, referenceId, priority, app);
		}
	}

	/** Notifies one user, unless it is the actor or the system account. */
	void notify(Long userId, Message message, Long actingUserId) {
		notifyAll(userId == null ? List.of() : List.of(userId), message, actingUserId);
	}

	/**
	 * Notifies the staff who may read {@code functionCode} (see {@code findStaffWhoCanRead}), but not the actor, as
	 * the alert's general setting says ({@link #asSet}): not at all when OFF.
	 */
	void notifyStaff(String alertKey, String functionCode, Message message, Long actingUserId) {
		Message alert = asSet(alertKey, message);
		if (alert != null) {
			notifyAll(userRoleRepository.findStaffWhoCanRead(functionCode), alert.forApp(STAFF), actingUserId);
		}
	}

	/**
	 * A staff alert as its setting (V49, Settings > General) wants it: null when OFF, always HIGH when HIGH, as the
	 * event made it when NORMAL (e.g. an urgent ticket stays HIGH).
	 */
	Message asSet(String alertKey, Message message) {
		return switch (settingsService.alertLevel(alertKey)) {
			case NexHomeSettingsServiceImpl.OFF -> null;
			case NexHomeSettingsServiceImpl.HIGH -> message.high();
			default -> message;
		};
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
			notification.setPriority(message.priority());
			notification.setAudience(audienceOf(userId, message));
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
		// To the recipients' phones too, once the transaction commits (nothing when no push provider is set)
		pushDispatcher.pushAfterCommit(notifications);
		return notifications;
	}

	/**
	 * The app a notification belongs to for this recipient, from what it is about (V54): a booking's customer
	 * CUSTOMER, its partner (or one it was offered to) PARTNER; a ticket's requester the app the ticket came from; a
	 * review's partner PARTNER and customer CUSTOMER; the partner or customer of a profile; anyone else STAFF (they hear
	 * of it as staff). Without a record (staff notes): ALL.
	 */
	String audienceOf(Long userId, Message message) {
		if (message.audience() != null) {
			return message.audience();
		}
		Long id = message.referenceId();
		if (message.referenceType() == null || id == null) {
			return ALL;
		}
		return switch (message.referenceType()) {
			case BOOKING -> bookingRepository.findById(id).map(booking -> {
				if (booking.getCustomer().getUser().getId().equals(userId)) {
					return CUSTOMER;
				}
				boolean partner = (booking.getPartner() != null && booking.getPartner().getUser().getId().equals(userId))
						|| assignmentRepository.existsByBookingIdAndPartnerUserId(id, userId);
				return partner ? PARTNER : STAFF;
			}).orElse(ALL);
			case SUPPORT_TICKET -> ticketRepository.findById(id)
					.map(ticket -> ticket.getRequester().getId().equals(userId) ? ticket.getRequesterRole() : STAFF)
					.orElse(ALL);
			case REVIEW -> reviewRepository.findById(id).map(review -> {
				if (review.getPartner().getUser().getId().equals(userId)) {
					return PARTNER;
				}
				return review.getCustomer().getUser().getId().equals(userId) ? CUSTOMER : STAFF;
			}).orElse(ALL);
			case PARTNER_PROFILE -> partnerProfileRepository.findById(id)
					.map(profile -> profile.getUser().getId().equals(userId) ? PARTNER : STAFF)
					.orElse(ALL);
			case CUSTOMER_PROFILE -> customerProfileRepository.findById(id)
					.map(profile -> profile.getUser().getId().equals(userId) ? CUSTOMER : STAFF)
					.orElse(ALL);
			default -> ALL;
		};
	}
}
