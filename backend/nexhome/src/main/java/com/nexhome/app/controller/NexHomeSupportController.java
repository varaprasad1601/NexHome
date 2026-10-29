package com.nexhome.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeSupportAttachmentCreateRequest;
import com.nexhome.app.dto.NexHomeSupportAttachmentResponse;
import com.nexhome.app.dto.NexHomeSupportMessageCreateRequest;
import com.nexhome.app.dto.NexHomeSupportMessageResponse;
import com.nexhome.app.dto.NexHomeSupportStatsResponse;
import com.nexhome.app.dto.NexHomeSupportTicketCreateRequest;
import com.nexhome.app.dto.NexHomeSupportTicketDetailResponse;
import com.nexhome.app.dto.NexHomeSupportTicketResponse;
import com.nexhome.app.dto.NexHomeSupportTicketUpdateRequest;
import com.nexhome.app.service.NexHomeSupportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Module 9, support: users open tickets and reply; staff with SUPPORT_MANAGE see every ticket, reply, add internal
 * notes, assign and move them on. Owner rules are checked in the service.
 */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class NexHomeSupportController {

	private final NexHomeSupportService supportService;

	/** Anyone for themself; for another user SUPPORT_MANAGE write (checked in the service). */
	@PostMapping("/tickets/create")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeSupportTicketResponse createTicket(@Valid @RequestBody NexHomeSupportTicketCreateRequest request) {
		return supportService.createTicket(request);
	}

	@PutMapping("/tickets/update")
	@PreAuthorize("@permissions.can('SUPPORT_MANAGE', 'WRITE')")
	public NexHomeSupportTicketResponse updateTicket(@Valid @RequestBody NexHomeSupportTicketUpdateRequest request) {
		return supportService.updateTicket(request);
	}

	/** The requester or SUPPORT_MANAGE write; internal notes staff only (checked in the service). */
	@PostMapping("/tickets/messages/add")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeSupportMessageResponse addMessage(@Valid @RequestBody NexHomeSupportMessageCreateRequest request) {
		return supportService.addMessage(request);
	}

	@PostMapping("/tickets/attachments/add")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeSupportAttachmentResponse addAttachment(
			@Valid @RequestBody NexHomeSupportAttachmentCreateRequest request) {
		return supportService.addAttachment(request);
	}

	@GetMapping("/tickets")
	@PreAuthorize("@permissions.can('SUPPORT_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeSupportTicketResponse> getTickets(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String status,
			@RequestParam(required = false) String priority, @RequestParam(required = false) String category,
			@RequestParam(required = false) Long assignedTo, @RequestParam(required = false) Long requesterId,
			@RequestParam(required = false) Long bookingId, @RequestParam(required = false) String search) {
		return supportService.getTickets(page, size, status, priority, category, assignedTo, requesterId, bookingId,
				search);
	}

	@GetMapping("/tickets/stats")
	@PreAuthorize("@permissions.can('SUPPORT_MANAGE', 'READ')")
	public NexHomeSupportStatsResponse getStats() {
		return supportService.getStats();
	}

	@GetMapping("/assignees")
	@PreAuthorize("@permissions.can('SUPPORT_MANAGE', 'READ')")
	public java.util.List<com.nexhome.app.dto.NexHomeSupportAssigneeResponse> getAssignees() {
		return supportService.getAssignees();
	}

	@GetMapping("/tickets/me")
	public NexHomePageResponse<NexHomeSupportTicketResponse> getMyTickets(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return supportService.getMyTickets(page, size);
	}

	/** The requester (without internal notes) or SUPPORT_MANAGE read (checked in the service). */
	@GetMapping("/tickets/{supportTicketId}")
	public NexHomeSupportTicketDetailResponse getTicket(@PathVariable Long supportTicketId) {
		return supportService.getTicket(supportTicketId);
	}
}
