package com.nexhome.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePaymentOrder;

@Repository
public interface NexHomePaymentOrderRepository extends JpaRepository<NexHomePaymentOrder, Long> {

	Optional<NexHomePaymentOrder> findByIdempotencyKey(String idempotencyKey);

	Optional<NexHomePaymentOrder> findByProviderAndProviderOrderId(String provider, String providerOrderId);

	List<NexHomePaymentOrder> findByCheckoutIdOrderByIdDesc(UUID checkoutId);

	long countByPaymentStatusIn(List<String> statuses);

	/** Newest first, with the customers. */
	@EntityGraph(attributePaths = "customer")
	Page<NexHomePaymentOrder> findAllByOrderByCreationDateDescIdDesc(Pageable pageable);
}
