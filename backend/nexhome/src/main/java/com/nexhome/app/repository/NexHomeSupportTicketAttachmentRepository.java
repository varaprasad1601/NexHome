package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeSupportTicketAttachment;

@Repository
public interface NexHomeSupportTicketAttachmentRepository extends JpaRepository<NexHomeSupportTicketAttachment, Long> {

	List<NexHomeSupportTicketAttachment> findByTicketIdOrderByIdAsc(Long ticketId);
}
