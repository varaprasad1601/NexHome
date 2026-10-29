package com.nexhome.app.mapper;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeSupportAttachmentResponse;
import com.nexhome.app.dto.NexHomeSupportMessageResponse;
import com.nexhome.app.dto.NexHomeSupportTicketResponse;
import com.nexhome.app.model.NexHomeSupportTicket;
import com.nexhome.app.model.NexHomeSupportTicketAttachment;
import com.nexhome.app.model.NexHomeSupportTicketMessage;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.service.NexHomeAuditUsers.Person;

/** Support tickets, messages and attachments; names and roles come from {@code NexHomeAuditUsers.people}. */
@Component
public class NexHomeSupportMapper {

	private static final Person NOBODY = new Person(null, null);

	public NexHomeSupportTicketResponse toTicketDto(NexHomeSupportTicket ticket, Map<Long, Person> people) {
		NexHomeUser requester = ticket.getRequester();
		NexHomeUser assignee = ticket.getAssignedTo();
		Person requesterPerson = people.getOrDefault(requester.getId(), NOBODY);
		Person assigneePerson = assignee == null ? NOBODY : people.getOrDefault(assignee.getId(), NOBODY);
		Person creator = ticket.getCreatedBy() == null ? NOBODY : people.getOrDefault(ticket.getCreatedBy(), NOBODY);
		return new NexHomeSupportTicketResponse(ticket.getId(), ticket.getPublicId(), requester.getId(),
				requesterPerson.name(), requesterPerson.role(),
				requester.getCountryCode() + " " + requester.getMobileNumber(),
				ticket.getBooking() == null ? null : ticket.getBooking().getId(),
				ticket.getBooking() == null ? null : ticket.getBooking().getPublicId(),
				ticket.getPayment() == null ? null : ticket.getPayment().getId(),
				ticket.getPayment() == null ? null : ticket.getPayment().getProviderPaymentId(), ticket.getCategory(),
				ticket.getSubject(), ticket.getDescription(), ticket.getPriority(), ticket.getStatus(),
				assignee == null ? null : assignee.getId(), assigneePerson.name(), assigneePerson.role(),
				ticket.getResolvedAt(), ticket.getClosedAt(), ticket.getCreationDate(), ticket.getCreatedBy(),
				creator.name(), ticket.getUpdatedDate(), ticket.getUpdatedBy());
	}

	public NexHomeSupportMessageResponse toMessageDto(NexHomeSupportTicketMessage message, Long requesterId,
			Map<Long, Person> people) {
		Long senderId = message.getSender().getId();
		Person sender = people.getOrDefault(senderId, NOBODY);
		return new NexHomeSupportMessageResponse(message.getId(), senderId, sender.name(), sender.role(),
				!senderId.equals(requesterId), message.getMessage(), message.isInternalNote(),
				message.getCreationDate());
	}

	public NexHomeSupportAttachmentResponse toAttachmentDto(NexHomeSupportTicketAttachment attachment) {
		return new NexHomeSupportAttachmentResponse(attachment.getId(),
				attachment.getSupportMessage() == null ? null : attachment.getSupportMessage().getId(),
				attachment.getStorageKey(), attachment.getFileType(), attachment.getCreatedBy(),
				attachment.getCreationDate());
	}
}
