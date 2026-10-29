package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerTransfer;

@Repository
public interface NexHomePartnerTransferRepository extends JpaRepository<NexHomePartnerTransfer, Long> {

	List<NexHomePartnerTransfer> findByPayoutIdOrderByIdDesc(Long payoutId);

	/** Newest first, with the payouts. */
	@EntityGraph(attributePaths = { "payout", "payout.partner" })
	Page<NexHomePartnerTransfer> findAllByOrderByCreationDateDescIdDesc(Pageable pageable);
}
