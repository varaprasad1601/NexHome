package com.nexhome.app.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerDeduction;

@Repository
public interface NexHomePartnerDeductionRepository extends JpaRepository<NexHomePartnerDeduction, Long> {

	/** Newest first (by the day deducted, then when recorded), with the partners and linked payouts. */
	@EntityGraph(attributePaths = { "partner", "payout" })
	@Query("select d from NexHomePartnerDeduction d order by d.deductedOn desc, d.id desc")
	Page<NexHomePartnerDeduction> findAllNewestFirst(Pageable pageable);

	@EntityGraph(attributePaths = { "partner", "payout" })
	@Query("select d from NexHomePartnerDeduction d where d.partner.id = :partnerId "
			+ "order by d.deductedOn desc, d.id desc")
	Page<NexHomePartnerDeduction> findByPartnerNewestFirst(@Param("partnerId") Long partnerId, Pageable pageable);

	/** What NexHome kept from the partner (RECORDED deductions). */
	@Query("select coalesce(sum(d.amount), 0) from NexHomePartnerDeduction d where d.partner.id = :partnerId "
			+ "and d.deductionStatus = 'RECORDED'")
	BigDecimal sumDeductedOfPartner(@Param("partnerId") Long partnerId);

	@Query("select count(d) from NexHomePartnerDeduction d where d.partner.id = :partnerId "
			+ "and d.deductionStatus = 'RECORDED'")
	long countDeductedOfPartner(@Param("partnerId") Long partnerId);

	/** [partner id, deducted] of every partner with RECORDED deductions. */
	@Query("select d.partner.id, sum(d.amount) from NexHomePartnerDeduction d where d.deductionStatus = 'RECORDED' "
			+ "group by d.partner.id")
	List<Object[]> deductedByPartner();

	/** [count, amount] of all RECORDED deductions. */
	@Query("select count(d), coalesce(sum(d.amount), 0) from NexHomePartnerDeduction d "
			+ "where d.deductionStatus = 'RECORDED'")
	List<Object[]> totals();

	/** The deductions recorded with these payouts. */
	List<NexHomePartnerDeduction> findByPayoutIdIn(java.util.Collection<Long> payoutIds);
}
