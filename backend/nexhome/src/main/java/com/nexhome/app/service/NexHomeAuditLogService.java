package com.nexhome.app.service;

import java.time.Instant;

import com.nexhome.app.dto.NexHomeAuditLogFiltersResponse;
import com.nexhome.app.dto.NexHomeAuditLogResponse;
import com.nexhome.app.dto.NexHomePageResponse;

/** Module 9: reading the audit history (AUDIT_LOG_VIEW read). Entries are written by {@link NexHomeAuditRecorder}. */
public interface NexHomeAuditLogService {

	/** Newest first, only the filters given; {@code from} / {@code to}: [from, to) by when they were logged. */
	NexHomePageResponse<NexHomeAuditLogResponse> getAuditLogs(Integer page, Integer size, String entityType,
			String entityId, String action, Long actorUserId, Instant from, Instant to, String search);

	NexHomeAuditLogFiltersResponse getFilters();
}
