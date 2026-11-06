package com.nexhome.app.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerSettlement;

@Repository
public interface NexHomePartnerSettlementRepository extends JpaRepository<NexHomePartnerSettlement, Long> {

	Page<NexHomePartnerSettlement> findByPartnerIdOrderByCreationDateDescIdDesc(Long partnerId, Pageable pageable);

	Optional<NexHomePartnerSettlement> findFirstByPartnerIdAndSettlementStatusOrderByPaidAtDescIdDesc(Long partnerId,
			String settlementStatus);

	long countByPartnerId(Long partnerId);

	/** What NexHome has paid the partner through wallet payouts. */
	@Query("select coalesce(sum(s.netAmount), 0) from NexHomePartnerSettlement s where s.partner.id = :partnerId "
			+ "and s.settlementStatus = 'PAID'")
	BigDecimal sumPaidOfPartner(@Param("partnerId") Long partnerId);
}
