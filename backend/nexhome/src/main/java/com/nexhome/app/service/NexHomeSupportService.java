package com.nexhome.app.service;

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

/** Module 9: support tickets, their messages and internal notes, and attachments. */
public interface NexHomeSupportService {

	/** Any signed-in user for themself; for someone else SUPPORT_MANAGE write. */
	NexHomeSupportTicketResponse createTicket(NexHomeSupportTicketCreateRequest request);

	/** SUPPORT_MANAGE write: category, priority, status, assignee. */
	NexHomeSupportTicketResponse updateTicket(NexHomeSupportTicketUpdateRequest request);

	/** The requester, or SUPPORT_MANAGE write (internal notes: staff only). */
	NexHomeSupportMessageResponse addMessage(NexHomeSupportMessageCreateRequest request);

	/** The requester, or SUPPORT_MANAGE write. */
	NexHomeSupportAttachmentResponse addAttachment(NexHomeSupportAttachmentCreateRequest request);

	/** The requester (no internal notes), or SUPPORT_MANAGE read. */
	NexHomeSupportTicketDetailResponse getTicket(Long supportTicketId);

	/** SUPPORT_MANAGE read: paged, newest first, only the filters given. */
	NexHomePageResponse<NexHomeSupportTicketResponse> getTickets(Integer page, Integer size, String status,
			String priority, String category, Long assignedTo, Long requesterId, Long bookingId, String search);

	/** The caller's own tickets. */
	NexHomePageResponse<NexHomeSupportTicketResponse> getMyTickets(Integer page, Integer size);

	/** SUPPORT_MANAGE read. */
	NexHomeSupportStatsResponse getStats();

	/** SUPPORT_MANAGE read: who tickets can be assigned to, by name. */
	java.util.List<com.nexhome.app.dto.NexHomeSupportAssigneeResponse> getAssignees();
}
