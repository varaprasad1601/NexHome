package com.nexhome.app.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeNotificationDeliveryLogResponse;
import com.nexhome.app.dto.NexHomeNotificationReadRequest;
import com.nexhome.app.dto.NexHomeNotificationResponse;
import com.nexhome.app.dto.NexHomeNotificationSendRequest;
import com.nexhome.app.dto.NexHomeNotificationSendResponse;
import com.nexhome.app.dto.NexHomeNotificationStatsResponse;
import com.nexhome.app.dto.NexHomeNotificationSummaryResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeNotificationMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeLookupCodes.NotificationType;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeNotification;
import com.nexhome.app.model.NexHomeNotificationDeliveryLog;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.repository.NexHomeNotificationDeliveryLogRepository;
import com.nexhome.app.repository.NexHomeNotificationRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NexHomeNotificationServiceImpl implements NexHomeNotificationService {

	/** Who a sent notification goes to: one user, or every active holder of the roles. */
	private static final Map<String, List<String>> AUDIENCES = Map.of(
			"CUSTOMERS", List.of(NexHomeRole.CUSTOMER),
			"PARTNERS", List.of(NexHomeRole.PARTNER),
			"STAFF", List.of(NexHomeRole.SUPER_USER, NexHomeRole.ADMIN, NexHomeRole.SUB_ADMIN));
	private static final String USER = "USER";

	private final NexHomeNotificationRepository notificationRepository;
	private final NexHomeNotificationDeliveryLogRepository deliveryLogRepository;
	private final NexHomeUserRepository userRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeAuditUsers auditUsers;
	private final NexHomeNotificationMapper notificationMapper;
	private final NexHomeNotifier notifier;
	private final NexHomeAuditRecorder auditRecorder;

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeNotificationResponse> getMyNotifications(Integer page, Integer size,
			Boolean unread, String notificationType) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomeNotification> notifications = notificationRepository.findAll(
				NexHomeNotificationRepository.notificationSpec(permissionService.currentUserId(),
						typeOrNull(notificationType), unread, null), pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, notifications,
				notifications.stream().map(notification -> notificationMapper.toNotificationDto(notification, null))
						.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeNotificationSummaryResponse getMySummary() {
		Long userId = permissionService.currentUserId();
		Map<String, Long> unreadByType = new LinkedHashMap<>();
		long unread = 0;
		for (Object[] row : notificationRepository.countUnreadByType(userId)) {
			long count = ((Number) row[1]).longValue();
			unreadByType.put((String) row[0], count);
			unread += count;
		}
		return new NexHomeNotificationSummaryResponse(notificationRepository.countByUserId(userId), unread,
				unreadByType);
	}

	@Override
	@Transactional
	public int markRead(NexHomeNotificationReadRequest request) {
		return notificationRepository.markRead(permissionService.currentUserId(), request.notificationIds(),
				Instant.now());
	}

	@Override
	@Transactional
	public int markAllRead() {
		return notificationRepository.markAllRead(permissionService.currentUserId(), Instant.now());
	}

	@Override
	@Transactional
	public void deleteMine(Long notificationId) {
		NexHomeNotification notification = notificationRepository.findById(notificationId)
				.filter(found -> found.getUser().getId().equals(permissionService.currentUserId()))
				.orElseThrow(() -> new ResourceNotFoundException("Notification does not exist."));
		notificationRepository.delete(notification);
	}

	@Override
	@Transactional
	public int clearMine() {
		return notificationRepository.deleteAllOfUser(permissionService.currentUserId());
	}

	@Override
	@Transactional
	public NexHomeNotificationSendResponse send(NexHomeNotificationSendRequest request) {
		Long actingUserId = permissionService.currentUserId();
		String audience = request.audience().trim().toUpperCase();
		String type = NexHomeText.trimToNull(request.notificationType()) == null ? NotificationType.ANNOUNCEMENT
				: lookupService.validateCode(Types.NOTIFICATION_TYPE, request.notificationType(), "Notification type");
		List<Long> recipients;
		if (USER.equals(audience)) {
			if (request.userId() == null) {
				throw new InvalidRequestException("User ID is required to notify one user.");
			}
			userRepository.findById(request.userId())
					.filter(user -> AccountStatus.ACTIVE.equals(user.getAccountStatus()))
					.orElseThrow(() -> new ResourceNotFoundException("User does not exist or is not active."));
			recipients = List.of(request.userId());
		}
		else if (AUDIENCES.containsKey(audience)) {
			recipients = userRoleRepository.findActiveHoldersOf(AUDIENCES.get(audience));
		}
		else {
			throw new InvalidRequestException("Audience must be one of: USER, CUSTOMERS, PARTNERS, STAFF.");
		}
		// Staff sending to their own group get it too: only the sender is left out of a group
		List<NexHomeNotification> sent = notifier.notifyAll(recipients, new NexHomeNotifier.Message(type,
				request.title().trim(), request.message().trim(), null, null), actingUserId);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("audience", audience);
		details.put("userId", request.userId());
		details.put("notificationType", type);
		details.put("title", request.title().trim());
		details.put("recipients", sent.size());
		auditRecorder.record("NOTIFICATION_SENT", "NOTIFICATION", audience, null, details, actingUserId);
		log.info("Notification sent to {} ({} users) by User ID {}", audience, sent.size(), actingUserId);
		return new NexHomeNotificationSendResponse(audience, sent.size());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeNotificationResponse> getNotifications(Integer page, Integer size, Long userId,
			String notificationType, Boolean unread, String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomeNotification> notifications = notificationRepository.findAll(
				NexHomeNotificationRepository.notificationSpec(userId, typeOrNull(notificationType), unread,
						NexHomePaging.searchText(search)), pageRequest);
		Set<Long> userIds = new HashSet<>();
		notifications.forEach(notification -> userIds.add(notification.getUser().getId()));
		Map<Long, NexHomeAuditUsers.Person> people = auditUsers.people(userIds);
		return NexHomePaging.toPageResponse(pageRequest, notifications, notifications.stream()
				.map(notification -> notificationMapper.toNotificationDto(notification,
						people.get(notification.getUser().getId())))
				.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeNotificationStatsResponse getStats() {
		Map<String, Long> byType = new LinkedHashMap<>();
		long total = 0;
		for (Object[] row : notificationRepository.countByType()) {
			long count = ((Number) row[1]).longValue();
			byType.put((String) row[0], count);
			total += count;
		}
		Map<String, Long> deliveries = new LinkedHashMap<>();
		deliveryLogRepository.countByStatus().forEach(row -> deliveries.put((String) row[0],
				((Number) row[1]).longValue()));
		return new NexHomeNotificationStatsResponse(total, notificationRepository.countUnread(), byType, deliveries);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeNotificationDeliveryLogResponse> getDeliveryLogs(Integer page, Integer size,
			String channel, String deliveryStatus, Long userId) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		String channelCode = NexHomeText.trimToNull(channel) == null ? null
				: lookupService.validateCode(Types.NOTIFICATION_CHANNEL, channel, "Channel");
		String statusCode = NexHomeText.trimToNull(deliveryStatus) == null ? null
				: lookupService.validateCode(Types.DELIVERY_STATUS, deliveryStatus, "Delivery status");
		Page<NexHomeNotificationDeliveryLog> logs = deliveryLogRepository.findAll(
				NexHomeNotificationDeliveryLogRepository.deliverySpec(channelCode, statusCode, userId), pageRequest);
		Set<Long> userIds = new HashSet<>();
		logs.forEach(entry -> userIds.add(entry.getUser().getId()));
		Map<Long, NexHomeAuditUsers.Person> people = auditUsers.people(userIds);
		return NexHomePaging.toPageResponse(pageRequest, logs, logs.stream()
				.map(entry -> notificationMapper.toDeliveryLogDto(entry, people.get(entry.getUser().getId())))
				.toList());
	}

	private String typeOrNull(String notificationType) {
		return NexHomeText.trimToNull(notificationType) == null ? null
				: lookupService.validateCode(Types.NOTIFICATION_TYPE, notificationType, "Notification type");
	}
}
