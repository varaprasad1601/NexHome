package com.nexhome.app.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeAuditLogFiltersResponse;
import com.nexhome.app.dto.NexHomeAuditLogResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAuditLog;
import com.nexhome.app.repository.NexHomeAuditLogRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class NexHomeAuditLogServiceImpl implements NexHomeAuditLogService {

	private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
	};

	private final NexHomeAuditLogRepository auditLogRepository;
	private final NexHomeAuditUsers auditUsers;
	private final JsonMapper jsonMapper;

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeAuditLogResponse> getAuditLogs(Integer page, Integer size, String entityType,
			String entityId, String action, Long actorUserId, Instant from, Instant to, String search) {
		if (from != null && to != null && !to.isAfter(from)) {
			throw new InvalidRequestException("To must be after from.");
		}
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomeAuditLog> logs = auditLogRepository.findAll(NexHomeAuditLogRepository.auditSpec(
				NexHomeText.trimToNull(entityType), NexHomeText.trimToNull(entityId), NexHomeText.trimToNull(action),
				actorUserId, from, to, NexHomePaging.searchText(search)), pageRequest);
		Set<Long> actorIds = new HashSet<>();
		logs.forEach(log -> {
			if (log.getActor() != null) {
				actorIds.add(log.getActor().getId());
			}
		});
		Map<Long, NexHomeAuditUsers.Person> people = auditUsers.people(actorIds);
		return NexHomePaging.toPageResponse(pageRequest, logs, logs.stream().map(log -> {
			Long actorId = log.getActor() == null ? null : log.getActor().getId();
			NexHomeAuditUsers.Person actor = actorId == null ? null : people.get(actorId);
			return new NexHomeAuditLogResponse(log.getId(), actorId, actor == null ? null : actor.name(),
					actor == null ? null : actor.role(), log.getAction(), log.getEntityType(), log.getEntityId(),
					readObject(log.getOldValues()), readObject(log.getNewValues()),
					log.getIpAddress() == null ? null : log.getIpAddress().getHostAddress(), log.getCreationDate());
		}).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeAuditLogFiltersResponse getFilters() {
		Map<String, List<String>> actions = new LinkedHashMap<>();
		auditLogRepository.findEntityTypesAndActions().forEach(row -> actions
				.computeIfAbsent((String) row[0], type -> new ArrayList<>()).add((String) row[1]));
		return new NexHomeAuditLogFiltersResponse(List.copyOf(actions.keySet()), actions);
	}

	private Map<String, Object> readObject(String json) {
		return json == null ? null : jsonMapper.readValue(json, JSON_OBJECT);
	}
}
