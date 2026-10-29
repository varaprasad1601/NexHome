package com.nexhome.app.controller;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeAuditLogFiltersResponse;
import com.nexhome.app.dto.NexHomeAuditLogResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.service.NexHomeAuditLogService;

import lombok.RequiredArgsConstructor;

/** Module 9, audit: the security and business history, read only (AUDIT_LOG_VIEW read). */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class NexHomeAuditLogController {

	private final NexHomeAuditLogService auditLogService;

	@GetMapping
	@PreAuthorize("@permissions.can('AUDIT_LOG_VIEW', 'READ')")
	public NexHomePageResponse<NexHomeAuditLogResponse> getAuditLogs(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String entityType,
			@RequestParam(required = false) String entityId, @RequestParam(required = false) String action,
			@RequestParam(required = false) Long actorUserId, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(required = false) String search) {
		return auditLogService.getAuditLogs(page, size, entityType, entityId, action, actorUserId, from, to, search);
	}

	@GetMapping("/filters")
	@PreAuthorize("@permissions.can('AUDIT_LOG_VIEW', 'READ')")
	public NexHomeAuditLogFiltersResponse getFilters() {
		return auditLogService.getFilters();
	}
}
