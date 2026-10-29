package com.nexhome.app.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeNotificationDeliveryLogResponse;
import com.nexhome.app.dto.NexHomeNotificationReadRequest;
import com.nexhome.app.dto.NexHomeNotificationResponse;
import com.nexhome.app.dto.NexHomeNotificationSendRequest;
import com.nexhome.app.dto.NexHomeNotificationSendResponse;
import com.nexhome.app.dto.NexHomeNotificationStatsResponse;
import com.nexhome.app.dto.NexHomeNotificationSummaryResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.service.NexHomeNotificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Module 9, notifications: every signed-in user's own (the {@code /me} endpoints), and for NOTIFICATION_MANAGE
 * sending them and seeing every notification and delivery log.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NexHomeNotificationController {

	private final NexHomeNotificationService notificationService;

	@GetMapping("/me")
	public NexHomePageResponse<NexHomeNotificationResponse> getMyNotifications(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) Boolean unread, @RequestParam(required = false) String notificationType) {
		return notificationService.getMyNotifications(page, size, unread, notificationType);
	}

	@GetMapping("/me/summary")
	public NexHomeNotificationSummaryResponse getMySummary() {
		return notificationService.getMySummary();
	}

	/** Returns {@code {"updated": n}}. */
	@PutMapping("/me/read")
	public Map<String, Integer> markRead(@Valid @RequestBody NexHomeNotificationReadRequest request) {
		return Map.of("updated", notificationService.markRead(request));
	}

	@PutMapping("/me/read-all")
	public Map<String, Integer> markAllRead() {
		return Map.of("updated", notificationService.markAllRead());
	}

	@DeleteMapping("/me/delete/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMine(@PathVariable Long notificationId) {
		notificationService.deleteMine(notificationId);
	}

	@DeleteMapping("/me/clear")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void clearMine() {
		notificationService.clearMine();
	}

	@PostMapping("/send")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('NOTIFICATION_MANAGE', 'WRITE')")
	public NexHomeNotificationSendResponse send(@Valid @RequestBody NexHomeNotificationSendRequest request) {
		return notificationService.send(request);
	}

	@GetMapping
	@PreAuthorize("@permissions.can('NOTIFICATION_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeNotificationResponse> getNotifications(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) Long userId, @RequestParam(required = false) String notificationType,
			@RequestParam(required = false) Boolean unread, @RequestParam(required = false) String search) {
		return notificationService.getNotifications(page, size, userId, notificationType, unread, search);
	}

	@GetMapping("/stats")
	@PreAuthorize("@permissions.can('NOTIFICATION_MANAGE', 'READ')")
	public NexHomeNotificationStatsResponse getStats() {
		return notificationService.getStats();
	}

	@GetMapping("/delivery-logs")
	@PreAuthorize("@permissions.can('NOTIFICATION_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeNotificationDeliveryLogResponse> getDeliveryLogs(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String channel, @RequestParam(required = false) String deliveryStatus,
			@RequestParam(required = false) Long userId) {
		return notificationService.getDeliveryLogs(page, size, channel, deliveryStatus, userId);
	}
}
