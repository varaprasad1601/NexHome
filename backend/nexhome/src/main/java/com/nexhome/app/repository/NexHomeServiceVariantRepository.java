package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeServiceVariant;

@Repository
public interface NexHomeServiceVariantRepository extends JpaRepository<NexHomeServiceVariant, Long> {

	List<NexHomeServiceVariant> findByServiceIdOrderByDisplayOrderAscNameAsc(Long serviceId);

	/** (service_id, variant_code) is unique. */
	boolean existsByServiceIdAndVariantCode(Long serviceId, String variantCode);

	boolean existsByServiceId(Long serviceId);
}
