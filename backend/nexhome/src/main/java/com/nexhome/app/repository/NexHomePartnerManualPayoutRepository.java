package com.nexhome.app.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerManualPayout;

@Repository
public interface NexHomePartnerManualPayoutRepository extends JpaRepository<NexHomePartnerManualPayout, Long> {

	/** Newest payment first (by the day paid, then when recorded), with the partners. */
	@EntityGraph(attributePaths = { "partner" })
	@Query("select p from NexHomePartnerManualPayout p order by p.paidOn desc, p.id desc")
	Page<NexHomePartnerManualPayout> findAllNewestFirst(Pageable pageable);

	@EntityGraph(attributePaths = { "partner" })
	@Query("select p from NexHomePartnerManualPayout p where p.partner.id = :partnerId order by p.paidOn desc, p.id desc")
	Page<NexHomePartnerManualPayout> findByPartnerNewestFirst(@Param("partnerId") Long partnerId, Pageable pageable);

	/** What the partner has been paid (RECORDED payouts). */
	@Query("select coalesce(sum(p.amount), 0) from NexHomePartnerManualPayout p where p.partner.id = :partnerId "
			+ "and p.payoutStatus = 'RECORDED'")
	BigDecimal sumPaidOfPartner(@Param("partnerId") Long partnerId);

	@Query("select count(p) from NexHomePartnerManualPayout p where p.partner.id = :partnerId "
			+ "and p.payoutStatus = 'RECORDED'")
	long countPaidOfPartner(@Param("partnerId") Long partnerId);

	/** The latest RECORDED payout of a partner. */
	@EntityGraph(attributePaths = { "partner" })
	Optional<NexHomePartnerManualPayout> findFirstByPartnerIdAndPayoutStatusOrderByPaidOnDescIdDesc(Long partnerId,
			String payoutStatus);

	/** [partner id, paid] of every partner with RECORDED payouts. */
	@Query("select p.partner.id, sum(p.amount) from NexHomePartnerManualPayout p where p.payoutStatus = 'RECORDED' "
			+ "group by p.partner.id")
	List<Object[]> paidByPartner();

	/** [count, amount] of RECORDED payouts paid on or after a day. */
	@Query("select count(p), coalesce(sum(p.amount), 0) from NexHomePartnerManualPayout p "
			+ "where p.payoutStatus = 'RECORDED' and p.paidOn >= :from")
	List<Object[]> paidSince(@Param("from") LocalDate from);
}
