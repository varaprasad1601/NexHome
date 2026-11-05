package com.nexhome.app.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeLookupCodes.DeliveryStatus;
import com.nexhome.app.model.NexHomeLookupCodes.NotificationChannel;
import com.nexhome.app.model.NexHomeNotification;
import com.nexhome.app.model.NexHomeNotificationDeliveryLog;
import com.nexhome.app.model.NexHomePushDevice;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeNotificationDeliveryLogRepository;
import com.nexhome.app.repository.NexHomeNotificationRepository;
import com.nexhome.app.repository.NexHomePushDeviceRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Pushes in-app notifications to the recipients' phones (V53). {@link NexHomeNotifier} hands each batch over inside
 * the caller's transaction; the push goes out only after that transaction commits (a rolled-back notification is
 * never pushed), on a small background pool so requests do not wait for FCM. Each push becomes a delivery log
 * (channel PUSH: SENT with the FCM message id, or FAILED with the reason); a token FCM no longer knows is deactivated
 * (UNREGISTERED). Only ACTIVE accounts' active devices receive pushes. Package-private like the notifier.
 */
@Component
@Slf4j
class NexHomePushDispatcher {

	/** What a push needs, copied out of the transaction. */
	record Push(Long notificationId, Long userId, String title, String message, String notificationType,
			String referenceType, Long referenceId, String priority, String audience) {

		/** Whether a device of this app gets it (V54): ALL every app, else only its own. */
		boolean isFor(String appCode) {
			return audience == null || NexHomeNotifier.ALL.equals(audience) || audience.equals(appCode);
		}
	}

	private record Device(Long id, Long userId, String token, String appCode) {
	}

	private final NexHomePushSender sender;
	private final NexHomePushDeviceRepository deviceRepository;
	private final NexHomeNotificationRepository notificationRepository;
	private final NexHomeNotificationDeliveryLogRepository deliveryLogRepository;
	private final NexHomeUserRepository userRepository;
	private final TransactionTemplate transactions;
	private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "nexhome-push");
		thread.setDaemon(true);
		return thread;
	});

	NexHomePushDispatcher(NexHomePushSender sender, NexHomePushDeviceRepository deviceRepository,
			NexHomeNotificationRepository notificationRepository,
			NexHomeNotificationDeliveryLogRepository deliveryLogRepository, NexHomeUserRepository userRepository,
			PlatformTransactionManager transactionManager) {
		this.sender = sender;
		this.deviceRepository = deviceRepository;
		this.notificationRepository = notificationRepository;
		this.deliveryLogRepository = deliveryLogRepository;
		this.userRepository = userRepository;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@PreDestroy
	void stop() {
		executor.shutdown();
	}

	/** Pushes these stored notifications once the current transaction commits (at once without one). */
	void pushAfterCommit(Collection<NexHomeNotification> notifications) {
		if (!sender.enabled() || notifications.isEmpty()) {
			return;
		}
		List<Push> pushes = notifications.stream()
				.map(n -> new Push(n.getId(), n.getUser().getId(), n.getTitle(), n.getMessage(),
						n.getNotificationType(), n.getReferenceType(), n.getReferenceId(), n.getPriority(),
						n.getAudience()))
				.toList();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					submit(pushes);
				}
			});
		}
		else {
			submit(pushes);
		}
	}

	private void submit(List<Push> pushes) {
		try {
			executor.execute(() -> send(pushes));
		}
		catch (RuntimeException ex) {
			log.warn("Push notifications not queued: {}", ex.getMessage());
		}
	}

	/** Sends every push to its user's active devices and records the outcome. */
	void send(List<Push> pushes) {
		try {
			Set<Long> userIds = pushes.stream().map(Push::userId).collect(Collectors.toSet());
			Map<Long, List<Device>> devices = transactions.execute(status -> deviceRepository
					.findByUserIdInAndActiveTrue(userIds).stream()
					.filter(device -> AccountStatus.ACTIVE.equals(device.getUser().getAccountStatus()))
					.map(device -> new Device(device.getId(), device.getUser().getId(), device.getDeviceToken(),
							device.getAppCode()))
					.collect(Collectors.groupingBy(Device::userId)));
			if (devices == null || devices.isEmpty()) {
				return;
			}
			List<NexHomeNotificationDeliveryLog> logs = new ArrayList<>();
			List<Long> deadDevices = new ArrayList<>();
			for (Push push : pushes) {
				for (Device device : devices.getOrDefault(push.userId(), List.of())) {
					if (push.isFor(device.appCode())) {
						logs.add(sendOne(push, device, deadDevices));
					}
				}
			}
			transactions.executeWithoutResult(status -> {
				for (NexHomeNotificationDeliveryLog entry : logs) {
					entry.setNotification(notificationRepository.getReferenceById(entry.getNotification().getId()));
					entry.setUser(userRepository.getReferenceById(entry.getUser().getId()));
				}
				deliveryLogRepository.saveAll(logs);
				if (!deadDevices.isEmpty()) {
					deviceRepository.findAllById(deadDevices).forEach(device -> {
						device.setActive(false);
						device.setDeactivatedReason(NexHomePushDevice.UNREGISTERED);
					});
				}
			});
		}
		catch (RuntimeException ex) {
			log.warn("Push notifications could not be sent: {}", ex.getMessage());
		}
	}

	private NexHomeNotificationDeliveryLog sendOne(Push push, Device device, List<Long> deadDevices) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("notificationId", String.valueOf(push.notificationId()));
		data.put("notificationType", push.notificationType());
		data.put("priority", push.priority());
		if (push.referenceType() != null) {
			data.put("referenceType", push.referenceType());
			data.put("referenceId", String.valueOf(push.referenceId()));
		}
		NexHomeNotificationDeliveryLog entry = new NexHomeNotificationDeliveryLog();
		NexHomeNotification notification = new NexHomeNotification();
		notification.setId(push.notificationId());
		entry.setNotification(notification);
		NexHomeUser user = new NexHomeUser();
		user.setId(push.userId());
		entry.setUser(user);
		entry.setChannel(NotificationChannel.PUSH);
		entry.setSentAt(Instant.now());
		try {
			entry.setProviderMessageId(sender.send(device.token(), push.title(), push.message(), data,
					"HIGH".equals(push.priority())));
			entry.setDeliveryStatus(DeliveryStatus.SENT);
		}
		catch (NexHomePushSender.NexHomePushException ex) {
			entry.setDeliveryStatus(DeliveryStatus.FAILED);
			entry.setFailureReason(ex.getMessage());
			if (ex.unregistered()) {
				deadDevices.add(device.id());
			}
			log.info("Push for notification {} to device {} failed: {}", push.notificationId(), device.id(),
					ex.getMessage());
		}
		return entry;
	}
}
