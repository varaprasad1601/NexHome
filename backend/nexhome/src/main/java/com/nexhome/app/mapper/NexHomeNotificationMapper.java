package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeNotificationDeliveryLogResponse;
import com.nexhome.app.dto.NexHomeNotificationResponse;
import com.nexhome.app.model.NexHomeNotification;
import com.nexhome.app.model.NexHomeNotificationDeliveryLog;
import com.nexhome.app.service.NexHomeAuditUsers.Person;

@Component
public class NexHomeNotificationMapper {

	/** {@code person}: the recipient's name and role for the console's list; null for the user's own. */
	public NexHomeNotificationResponse toNotificationDto(NexHomeNotification notification, Person person) {
		return new NexHomeNotificationResponse(notification.getId(), notification.getUser().getId(),
				person == null ? null : person.name(), person == null ? null : person.role(),
				notification.getNotificationType(), notification.getTitle(), notification.getMessage(),
				notification.getReferenceType(), notification.getReferenceId(), notification.getReadAt() != null,
				notification.getReadAt(), notification.getCreationDate());
	}

	public NexHomeNotificationDeliveryLogResponse toDeliveryLogDto(NexHomeNotificationDeliveryLog log,
			Person person) {
		NexHomeNotification notification = log.getNotification();
		return new NexHomeNotificationDeliveryLogResponse(log.getId(),
				notification == null ? null : notification.getId(),
				notification == null ? null : notification.getTitle(), log.getUser().getId(),
				person == null ? null : person.name(), log.getChannel(), log.getProviderMessageId(),
				log.getDeliveryStatus(), log.getSentAt(), log.getDeliveredAt(), log.getFailureReason(),
				log.getCreationDate());
	}
}
