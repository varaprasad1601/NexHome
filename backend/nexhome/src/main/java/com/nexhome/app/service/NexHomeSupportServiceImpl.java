package com.nexhome.app.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeSupportMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeBooking;
import com.nexhome.app.model.NexHomeLookupCodes.NotificationType;
import com.nexhome.app.model.NexHomeLookupCodes.SupportPriority;
import com.nexhome.app.model.NexHomeLookupCodes.SupportTicketStatus;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomePayment;
import com.nexhome.app.model.NexHomeSupportTicket;
import com.nexhome.app.model.NexHomeSupportTicketAttachment;
import com.nexhome.app.model.NexHomeSupportTicketMessage;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeBookingRepository;
import com.nexhome.app.repository.NexHomePaymentRepository;
import com.nexhome.app.repository.NexHomeSupportTicketAttachmentRepository;
import com.nexhome.app.repository.NexHomeSupportTicketMessageRepository;
import com.nexhome.app.repository.NexHomeSupportTicketRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NexHomeSupportServiceImpl implements NexHomeSupportService {

	static final String SUPPORT_MANAGE = "SUPPORT_MANAGE";
	/** Statuses in which a ticket is done with. */
	private static final Set<String> FINISHED = Set.of(SupportTicketStatus.RESOLVED, SupportTicketStatus.CLOSED);

	private final NexHomeSupportTicketRepository ticketRepository;
	private final NexHomeSupportTicketMessageRepository messageRepository;
	private final NexHomeSupportTicketAttachmentRepository attachmentRepository;
	private final NexHomeUserRepository userRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeBookingRepository bookingRepository;
	private final NexHomePaymentRepository paymentRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeAuditUsers auditUsers;
	private final NexHomeSupportMapper supportMapper;
	private final NexHomeNotifier notifier;
	private final NexHomeAuditRecorder auditRecorder;

	@Override
	@Transactional
	public NexHomeSupportTicketResponse createTicket(NexHomeSupportTicketCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		boolean staff = permissionService.can(SUPPORT_MANAGE, NexHomeAccess.WRITE.name());
		Long requesterId = request.requesterId() == null ? actingUserId : request.requesterId();
		if (!requesterId.equals(actingUserId) && !staff) {
			throw new AccessDeniedException("You can only open support tickets for yourself.");
		}
		String category = lookupService.validateCode(Types.SUPPORT_CATEGORY, request.category(), "Category");
		String priority = NexHomeText.trimToNull(request.priority()) == null ? SupportPriority.MEDIUM
				: lookupService.validateCode(Types.SUPPORT_PRIORITY, request.priority(), "Priority");
		NexHomeUser requester = userRepository.findById(requesterId)
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));

		NexHomeBooking booking = request.bookingId() == null ? null : bookingRepository.findById(request.bookingId())
				.orElseThrow(() -> new ResourceNotFoundException("Booking does not exist."));
		NexHomePayment payment = request.paymentId() == null ? null : paymentRepository.findById(request.paymentId())
				.orElseThrow(() -> new ResourceNotFoundException("Payment does not exist."));
		if (payment != null) {
			if (booking != null && !payment.getBooking().getId().equals(booking.getId())) {
				throw new InvalidRequestException("The payment does not belong to the booking.");
			}
			booking = payment.getBooking();
		}
		if (booking != null && !staff && !involves(booking, requesterId)) {
			throw new InvalidRequestException("The booking is not yours.");
		}

		NexHomeSupportTicket ticket = new NexHomeSupportTicket();
		ticket.setRequester(requester);
		ticket.setBooking(booking);
		ticket.setPayment(payment);
		ticket.setCategory(category);
		ticket.setSubject(request.subject().trim());
		ticket.setDescription(request.description().trim());
		ticket.setPriority(priority);
		ticket.setStatus(SupportTicketStatus.OPEN);
		ticket.setCreatedBy(actingUserId);
		ticket.setUpdatedBy(actingUserId);
		ticketRepository.saveAndFlush(ticket);

		notifier.notifyStaff(SUPPORT_MANAGE, new NexHomeNotifier.Message(NotificationType.SUPPORT,
				"New support ticket " + ticket.getPublicId(), ticket.getSubject(), NexHomeNotifier.SUPPORT_TICKET,
				ticket.getId()), actingUserId);
		notifier.notify(requesterId, new NexHomeNotifier.Message(NotificationType.SUPPORT,
				"Support ticket " + ticket.getPublicId() + " opened",
				"We opened a support ticket for you: " + ticket.getSubject(), NexHomeNotifier.SUPPORT_TICKET,
				ticket.getId()), actingUserId);
		log.info("Support ticket {} opened for User ID {} by User ID {}", ticket.getPublicId(), requesterId,
				actingUserId);
		return supportMapper.toTicketDto(ticket, auditUsers.people(peopleOf(ticket)));
	}

	@Override
	@Transactional
	public NexHomeSupportTicketResponse updateTicket(NexHomeSupportTicketUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeSupportTicket ticket = findTicket(request.supportTicketId());
		// Every check that queries runs before the ticket changes
		String category = NexHomeText.trimToNull(request.category()) == null ? null
				: lookupService.validateCode(Types.SUPPORT_CATEGORY, request.category(), "Category");
		String priority = NexHomeText.trimToNull(request.priority()) == null ? null
				: lookupService.validateCode(Types.SUPPORT_PRIORITY, request.priority(), "Priority");
		String status = NexHomeText.trimToNull(request.status()) == null ? null
				: lookupService.validateCode(Types.SUPPORT_TICKET_STATUS, request.status(), "Status");
		boolean unassign = Boolean.TRUE.equals(request.unassign());
		if (unassign && request.assignedToUserId() != null) {
			throw new InvalidRequestException("Assign the ticket or take its assignee off, not both.");
		}
		NexHomeUser assignee = null;
		if (request.assignedToUserId() != null) {
			if (!userRoleRepository.findStaffWhoCanRead(SUPPORT_MANAGE).contains(request.assignedToUserId())) {
				throw new InvalidRequestException("The assignee must be an active staff member who can read support "
						+ "tickets.");
			}
			assignee = userRepository.getReferenceById(request.assignedToUserId());
		}
		String current = ticket.getStatus();
		if (SupportTicketStatus.CLOSED.equals(current) && status != null
				&& !Set.of(SupportTicketStatus.CLOSED, SupportTicketStatus.IN_PROGRESS).contains(status)) {
			throw new InvalidRequestException("A closed ticket can only be reopened (IN_PROGRESS).");
		}

		Map<String, Object> before = new LinkedHashMap<>();
		Map<String, Object> after = new LinkedHashMap<>();
		if (category != null && !category.equals(ticket.getCategory())) {
			before.put("category", ticket.getCategory());
			after.put("category", category);
			ticket.setCategory(category);
		}
		if (priority != null && !priority.equals(ticket.getPriority())) {
			before.put("priority", ticket.getPriority());
			after.put("priority", priority);
			ticket.setPriority(priority);
		}
		Long previousAssignee = ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId();
		boolean assigned = assignee != null && !assignee.getId().equals(previousAssignee);
		if (assigned || unassign && previousAssignee != null) {
			before.put("assignedTo", previousAssignee);
			after.put("assignedTo", assigned ? assignee.getId() : null);
			ticket.setAssignedTo(assigned ? assignee : null);
			// Assigning starts an OPEN ticket; taking the assignee off returns an ASSIGNED one to the queue
			if (status == null && assigned && SupportTicketStatus.OPEN.equals(current)) {
				status = SupportTicketStatus.ASSIGNED;
			}
			else if (status == null && !assigned && SupportTicketStatus.ASSIGNED.equals(current)) {
				status = SupportTicketStatus.OPEN;
			}
		}
		if (status != null && !status.equals(current)) {
			before.put("status", current);
			after.put("status", status);
			moveTo(ticket, status);
		}
		if (after.isEmpty()) {
			throw new InvalidRequestException("Nothing to change.");
		}
		ticket.setUpdatedBy(actingUserId);
		auditRecorder.record("TICKET_UPDATED", "SUPPORT_TICKET", ticket.getPublicId(), before, after, actingUserId);

		if (assigned) {
			notifier.notify(assignee.getId(), new NexHomeNotifier.Message(NotificationType.SUPPORT,
					"Ticket " + ticket.getPublicId() + " assigned to you", ticket.getSubject(),
					NexHomeNotifier.SUPPORT_TICKET, ticket.getId()), actingUserId);
		}
		if (after.containsKey("status")) {
			notifier.notify(ticket.getRequester().getId(), new NexHomeNotifier.Message(NotificationType.SUPPORT,
					"Ticket " + ticket.getPublicId() + " is " + readable(ticket.getStatus()), ticket.getSubject(),
					NexHomeNotifier.SUPPORT_TICKET, ticket.getId()), actingUserId);
		}
		log.info("Support ticket {} updated by User ID {}: {}", ticket.getPublicId(), actingUserId, after.keySet());
		return supportMapper.toTicketDto(ticket, auditUsers.people(peopleOf(ticket)));
	}

	@Override
	@Transactional
	public NexHomeSupportMessageResponse addMessage(NexHomeSupportMessageCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeSupportTicket ticket = findTicket(request.supportTicketId());
		boolean requester = ticket.getRequester().getId().equals(actingUserId);
		boolean staff = permissionService.can(SUPPORT_MANAGE, NexHomeAccess.WRITE.name());
		if (!requester && !staff) {
			throw new AccessDeniedException("Only the ticket's requester and staff can reply.");
		}
		boolean internal = Boolean.TRUE.equals(request.isInternalNote());
		if (internal && !staff) {
			throw new AccessDeniedException("Only staff can add internal notes.");
		}
		if (SupportTicketStatus.CLOSED.equals(ticket.getStatus())) {
			throw new InvalidRequestException("The ticket is closed; open a new one.");
		}

		NexHomeSupportTicketMessage message = new NexHomeSupportTicketMessage();
		message.setTicket(ticket);
		message.setSender(userRepository.getReferenceById(actingUserId));
		message.setMessage(request.message().trim());
		message.setInternalNote(internal);
		message.setCreatedBy(actingUserId);
		message.setUpdatedBy(actingUserId);
		messageRepository.save(message);
		ticket.setUpdatedBy(actingUserId);

		if (!internal && requester) {
			// The requester writing again reopens a resolved ticket and tells whoever handles it
			if (SupportTicketStatus.RESOLVED.equals(ticket.getStatus())) {
				moveTo(ticket, SupportTicketStatus.IN_PROGRESS);
			}
			NexHomeNotifier.Message reply = new NexHomeNotifier.Message(NotificationType.SUPPORT,
					"New reply on ticket " + ticket.getPublicId(), excerpt(message.getMessage()),
					NexHomeNotifier.SUPPORT_TICKET, ticket.getId());
			if (ticket.getAssignedTo() != null) {
				notifier.notify(ticket.getAssignedTo().getId(), reply, actingUserId);
			}
			else {
				notifier.notifyStaff(SUPPORT_MANAGE, reply, actingUserId);
			}
		}
		else if (!internal) {
			// Staff answering start the work on a waiting ticket
			if (Set.of(SupportTicketStatus.OPEN, SupportTicketStatus.ASSIGNED).contains(ticket.getStatus())) {
				moveTo(ticket, SupportTicketStatus.IN_PROGRESS);
			}
			notifier.notify(ticket.getRequester().getId(), new NexHomeNotifier.Message(NotificationType.SUPPORT,
					"New reply on ticket " + ticket.getPublicId(), excerpt(message.getMessage()),
					NexHomeNotifier.SUPPORT_TICKET, ticket.getId()), actingUserId);
		}
		Long requesterId = ticket.getRequester().getId();
		return supportMapper.toMessageDto(message, requesterId, auditUsers.people(Set.of(actingUserId)));
	}

	@Override
	@Transactional
	public NexHomeSupportAttachmentResponse addAttachment(NexHomeSupportAttachmentCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeSupportTicket ticket = findTicket(request.supportTicketId());
		if (!ticket.getRequester().getId().equals(actingUserId)
				&& !permissionService.can(SUPPORT_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Only the ticket's requester and staff can attach files.");
		}
		if (SupportTicketStatus.CLOSED.equals(ticket.getStatus())) {
			throw new InvalidRequestException("The ticket is closed; open a new one.");
		}
		NexHomeSupportTicketMessage message = null;
		if (request.messageId() != null) {
			message = messageRepository.findById(request.messageId())
					.filter(found -> found.getTicket().getId().equals(ticket.getId()))
					.orElseThrow(() -> new InvalidRequestException("The message is not on this ticket."));
		}
		NexHomeSupportTicketAttachment attachment = new NexHomeSupportTicketAttachment();
		attachment.setTicket(ticket);
		attachment.setSupportMessage(message);
		attachment.setStorageKey(request.storageKey().trim());
		attachment.setFileType(request.fileType().trim());
		attachment.setCreatedBy(actingUserId);
		attachment.setUpdatedBy(actingUserId);
		attachmentRepository.save(attachment);
		return supportMapper.toAttachmentDto(attachment);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeSupportTicketDetailResponse getTicket(Long supportTicketId) {
		NexHomeSupportTicket ticket = findTicket(supportTicketId);
		boolean staff = permissionService.can(SUPPORT_MANAGE, NexHomeAccess.READ.name());
		if (!staff && !ticket.getRequester().getId().equals(permissionService.currentUserId())) {
			throw new AccessDeniedException("You do not have permission to see this ticket.");
		}
		List<NexHomeSupportTicketMessage> messages = messageRepository
				.findByTicketIdOrderByCreationDateAscIdAsc(supportTicketId).stream()
				.filter(message -> staff || !message.isInternalNote())
				.toList();
		Set<Long> userIds = new HashSet<>(peopleOf(ticket));
		messages.forEach(message -> userIds.add(message.getSender().getId()));
		Map<Long, NexHomeAuditUsers.Person> people = auditUsers.people(userIds);
		Long requesterId = ticket.getRequester().getId();
		return new NexHomeSupportTicketDetailResponse(supportMapper.toTicketDto(ticket, people),
				messages.stream().map(message -> supportMapper.toMessageDto(message, requesterId, people)).toList(),
				attachmentRepository.findByTicketIdOrderByIdAsc(supportTicketId).stream()
						.map(supportMapper::toAttachmentDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeSupportTicketResponse> getTickets(Integer page, Integer size, String status,
			String priority, String category, Long assignedTo, Long requesterId, Long bookingId, String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		String statusCode = NexHomeText.trimToNull(status) == null ? null
				: lookupService.validateCode(Types.SUPPORT_TICKET_STATUS, status, "Status");
		String priorityCode = NexHomeText.trimToNull(priority) == null ? null
				: lookupService.validateCode(Types.SUPPORT_PRIORITY, priority, "Priority");
		String categoryCode = NexHomeText.trimToNull(category) == null ? null
				: lookupService.validateCode(Types.SUPPORT_CATEGORY, category, "Category");
		return pageOf(pageRequest, ticketRepository.findAll(NexHomeSupportTicketRepository.ticketSpec(statusCode,
				priorityCode, categoryCode, assignedTo, requesterId, bookingId, NexHomePaging.searchText(search)),
				pageRequest));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeSupportTicketResponse> getMyTickets(Integer page, Integer size) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		return pageOf(pageRequest, ticketRepository.findAll(NexHomeSupportTicketRepository.ticketSpec(null, null,
				null, null, permissionService.currentUserId(), null, null), pageRequest));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeSupportStatsResponse getStats() {
		Map<String, Long> byStatus = new LinkedHashMap<>();
		long total = 0;
		long open = 0;
		for (Object[] row : ticketRepository.countByStatus()) {
			long count = ((Number) row[1]).longValue();
			byStatus.put((String) row[0], count);
			total += count;
			if (!FINISHED.contains(row[0])) {
				open += count;
			}
		}
		return new NexHomeSupportStatsResponse(total, byStatus, open, ticketRepository.countOpenHighPriority(),
				ticketRepository.countOpenUnassigned());
	}

	@Override
	@Transactional(readOnly = true)
	public List<com.nexhome.app.dto.NexHomeSupportAssigneeResponse> getAssignees() {
		Map<Long, NexHomeAuditUsers.Person> people = auditUsers.people(
				userRoleRepository.findStaffWhoCanRead(SUPPORT_MANAGE));
		return people.entrySet().stream()
				.map(entry -> new com.nexhome.app.dto.NexHomeSupportAssigneeResponse(entry.getKey(),
						entry.getValue().name(), entry.getValue().role()))
				.sorted(java.util.Comparator.comparing(com.nexhome.app.dto.NexHomeSupportAssigneeResponse::name,
						java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
				.toList();
	}

	private NexHomePageResponse<NexHomeSupportTicketResponse> pageOf(PageRequest pageRequest,
			Page<NexHomeSupportTicket> tickets) {
		Set<Long> userIds = new HashSet<>();
		tickets.forEach(ticket -> userIds.addAll(peopleOf(ticket)));
		Map<Long, NexHomeAuditUsers.Person> people = auditUsers.people(userIds);
		return NexHomePaging.toPageResponse(pageRequest, tickets,
				tickets.stream().map(ticket -> supportMapper.toTicketDto(ticket, people)).toList());
	}

	/** Sets the status with its times: RESOLVED and CLOSED say when; reopening clears them. */
	private static void moveTo(NexHomeSupportTicket ticket, String status) {
		Instant now = Instant.now();
		if (SupportTicketStatus.RESOLVED.equals(status)) {
			ticket.setResolvedAt(now);
			ticket.setClosedAt(null);
		}
		else if (SupportTicketStatus.CLOSED.equals(status)) {
			ticket.setClosedAt(now);
		}
		else {
			ticket.setResolvedAt(null);
			ticket.setClosedAt(null);
		}
		ticket.setStatus(status);
	}

	/** The users a ticket's response names: requester, assignee and whoever opened it. */
	private static Set<Long> peopleOf(NexHomeSupportTicket ticket) {
		Set<Long> ids = new HashSet<>();
		ids.add(ticket.getRequester().getId());
		if (ticket.getAssignedTo() != null) {
			ids.add(ticket.getAssignedTo().getId());
		}
		if (ticket.getCreatedBy() != null) {
			ids.add(ticket.getCreatedBy());
		}
		return ids;
	}

	/** Whether the user is the booking's customer or partner. */
	private static boolean involves(NexHomeBooking booking, Long userId) {
		return booking.getCustomer().getUser().getId().equals(userId)
				|| booking.getPartner() != null && Objects.equals(booking.getPartner().getUser().getId(), userId);
	}

	/** IN_PROGRESS reads "In Progress". */
	static String readable(String code) {
		StringBuilder text = new StringBuilder();
		for (String word : code.toLowerCase().split("_")) {
			if (!word.isEmpty()) {
				text.append(text.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0)))
						.append(word.substring(1));
			}
		}
		return text.toString();
	}

	/** The start of a message, for a notification. */
	private static String excerpt(String message) {
		return message.length() <= 160 ? message : message.substring(0, 157) + "...";
	}

	private NexHomeSupportTicket findTicket(Long supportTicketId) {
		return ticketRepository.findById(supportTicketId)
				.orElseThrow(() -> new ResourceNotFoundException("Support ticket does not exist."));
	}
}
