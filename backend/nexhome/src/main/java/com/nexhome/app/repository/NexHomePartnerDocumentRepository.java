package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerDocument;

@Repository
public interface NexHomePartnerDocumentRepository extends JpaRepository<NexHomePartnerDocument, Long> {

	/** Newest first. */
	List<NexHomePartnerDocument> findByPartnerIdOrderByCreationDateDescIdDesc(Long partnerId);
}
