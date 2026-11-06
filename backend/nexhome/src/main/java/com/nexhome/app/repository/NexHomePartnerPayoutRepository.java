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

	/** A partner's payouts in the given statuses that no wallet payout has settled, with their bookings. */
	@Query("select p from NexHomePartnerPayout p join fetch p.booking where p.partner.id = :partnerId "
			+ "and p.settlement is null and p.payoutStatus in :statuses order by p.id")
	java.util.List<NexHomePartnerPayout> findUnsettled(
			@org.springframework.data.repository.query.Param("partnerId") Long partnerId,
			@org.springframework.data.repository.query.Param("statuses") java.util.Collection<String> statuses);

	@Query("select p from NexHomePartnerPayout p join fetch p.booking where p.settlement.id = :settlementId "
			+ "order by p.id")
	java.util.List<NexHomePartnerPayout> findBySettlement(
			@org.springframework.data.repository.query.Param("settlementId") Long settlementId);

	/** What was paid to the partner one booking at a time (before wallet payouts). */
	@Query("select coalesce(sum(p.netAmount), 0) from NexHomePartnerPayout p where p.partner.id = :partnerId "
			+ "and p.payoutStatus = 'PAID' and p.settlement is null")
	java.math.BigDecimal sumPaidSeparately(
			@org.springframework.data.repository.query.Param("partnerId") Long partnerId);

	/** How many payouts are in each status and what they total: [status, count, net]. */
	@Query("select p.payoutStatus, count(p), coalesce(sum(p.netAmount), 0) from NexHomePartnerPayout p "
			+ "group by p.payoutStatus")
	java.util.List<Object[]> totalsByStatus();

	/** The net of a partner's payouts that are not cancelled. */
	@Query("select coalesce(sum(p.netAmount), 0) from NexHomePartnerPayout p where p.partner.id = :partnerId "
			+ "and p.payoutStatus <> 'CANCELLED'")
	java.math.BigDecimal sumNetOfPartner(@org.springframework.data.repository.query.Param("partnerId") Long partnerId);
}
