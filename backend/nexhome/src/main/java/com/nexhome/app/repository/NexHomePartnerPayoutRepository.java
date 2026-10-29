package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerPayout;

@Repository
public interface NexHomePartnerPayoutRepository extends JpaRepository<NexHomePartnerPayout, Long>,
		JpaSpecificationExecutor<NexHomePartnerPayout> {

	/** A page of payouts with their partners and bookings. */
	@Override
	@EntityGraph(attributePaths = { "partner", "booking" })
	Page<NexHomePartnerPayout> findAll(Specification<NexHomePartnerPayout> spec, Pageable pageable);

	Optional<NexHomePartnerPayout> findByBookingId(Long bookingId);

	java.util.List<NexHomePartnerPayout> findByBookingIdIn(java.util.Collection<Long> bookingIds);

	/** How many payouts are in each status and what they total: [status, count, net]. */
	@Query("select p.payoutStatus, count(p), coalesce(sum(p.netAmount), 0) from NexHomePartnerPayout p "
			+ "group by p.payoutStatus")
	java.util.List<Object[]> totalsByStatus();

	/** The net of a partner's payouts that are not cancelled. */
	@Query("select coalesce(sum(p.netAmount), 0) from NexHomePartnerPayout p where p.partner.id = :partnerId "
			+ "and p.payoutStatus <> 'CANCELLED'")
	java.math.BigDecimal sumNetOfPartner(@org.springframework.data.repository.query.Param("partnerId") Long partnerId);
}
