package com.nexhome.app.service;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.nexhome.app.model.NexHomeAuditLog;
import com.nexhome.app.repository.NexHomeAuditLogRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the security and business audit history (Module 9) for the other services, inside the caller's
 * transaction, so an entry exists exactly when the change is saved. Callers pass only what may be kept: never OTPs,
 * tokens, bank or document numbers. The client's address comes from the current request (none for scheduled jobs).
 */
@Component
@RequiredArgsConstructor
class NexHomeAuditRecorder {

	private final NexHomeAuditLogRepository auditLogRepository;
	private final NexHomeUserRepository userRepository;
	private final JsonMapper jsonMapper;

	/**
	 * One entry: {@code action} (e.g. STATUS_CHANGED), the entity's type (e.g. USER) and id, and the values before and
	 * after (null when there is nothing to show, e.g. before a creation).
	 */
	void record(String action, String entityType, Object entityId, Map<String, ?> oldValues,
			Map<String, ?> newValues, Long actorUserId) {
		NexHomeAuditLog log = new NexHomeAuditLog();
		log.setActor(actorUserId == null ? null : userRepository.getReferenceById(actorUserId));
		log.setAction(action);
		log.setEntityType(entityType);
		log.setEntityId(String.valueOf(entityId));
		log.setOldValues(oldValues == null ? null : jsonMapper.writeValueAsString(oldValues));
		log.setNewValues(newValues == null ? null : jsonMapper.writeValueAsString(newValues));
		log.setIpAddress(NexHomePolicyServiceImpl.parseAddress(clientAddress()));
		log.setCreatedBy(actorUserId);
		log.setUpdatedBy(actorUserId);
		auditLogRepository.save(log);
	}

	/** Key, value, key, value, ... as an ordered map; unlike {@code Map.of} it keeps null values. */
	static Map<String, Object> values(Object... keysAndValues) {
		Map<String, Object> map = new java.util.LinkedHashMap<>();
		for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
			map.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return map;
	}

	private static String clientAddress() {
		return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
				? attributes.getRequest().getRemoteAddr()
				: null;
	}
}
