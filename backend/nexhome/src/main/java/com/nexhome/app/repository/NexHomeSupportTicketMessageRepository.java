package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeSupportTicketMessage;

@Repository
public interface NexHomeSupportTicketMessageRepository extends JpaRepository<NexHomeSupportTicketMessage, Long> {

	/** A ticket's messages and internal notes, oldest first, with their senders. */
	@EntityGraph(attributePaths = "sender")
	List<NexHomeSupportTicketMessage> findByTicketIdOrderByCreationDateAscIdAsc(Long ticketId);
}
