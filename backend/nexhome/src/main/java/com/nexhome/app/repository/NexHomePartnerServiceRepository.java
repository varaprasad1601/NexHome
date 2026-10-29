package com.nexhome.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerService;

@Repository
public interface NexHomePartnerServiceRepository extends JpaRepository<NexHomePartnerService, Long> {

	/** The primary service first, then newest first, with the services loaded in the same query. */
	// The service and its category, for the list's names, in the same query
	@EntityGraph(attributePaths = { "service", "service.category" })
	List<NexHomePartnerService> findByPartnerIdOrderByPrimaryServiceDescCreationDateDescIdDesc(Long partnerId);

	/** (partner_id, service_id) is unique. */
	boolean existsByPartnerIdAndServiceId(Long partnerId, Long serviceId);

	/** The partner's primary service; unique by the partial index ux_nexhome_partner_services_one_primary. */
	Optional<NexHomePartnerService> findByPartnerIdAndPrimaryServiceTrue(Long partnerId);

	boolean existsByServiceId(Long serviceId);

	/** The partners offering a service (active rows), with the partners and their accounts in the same query. */
	@EntityGraph(attributePaths = { "partner", "partner.user" })
	List<NexHomePartnerService> findByServiceIdAndActiveTrue(Long serviceId);
}
