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
public interface NexHomePaymentOrderRepository extends JpaRepository<NexHomePaymentOrder, Long>,
		org.springframework.data.jpa.repository.JpaSpecificationExecutor<NexHomePaymentOrder> {

	/** A page of a filtered list, with the customers. */
	@Override
	@EntityGraph(attributePaths = "customer")
	Page<NexHomePaymentOrder> findAll(org.springframework.data.jpa.domain.Specification<NexHomePaymentOrder> spec,
			Pageable pageable);

	/**
	 * Only the filters given: the order's status, and a search on the order id, the gateway's order id, the
	 * customer (name or id) or a booking of the order's checkout (its id).
	 */
	static org.springframework.data.jpa.domain.Specification<NexHomePaymentOrder> orderSpec(String status,
			String search) {
		org.springframework.data.jpa.domain.Specification<NexHomePaymentOrder> spec = (order, query, cb) -> cb
				.conjunction();
		if (status != null) {
			spec = spec.and((order, query, cb) -> cb.equal(order.get("paymentStatus"), status));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((order, query, cb) -> {
				var bookings = query.subquery(Long.class);
				var booking = bookings.from(com.nexhome.app.model.NexHomeBooking.class);
				bookings.select(booking.get("id")).where(
						cb.equal(booking.get("checkoutId"), order.get("checkoutId")),
						cb.like(cb.lower(booking.get("publicId")), pattern, '\\'));
				return cb.or(cb.like(cb.lower(order.get("publicId")), pattern, '\\'),
						cb.like(cb.lower(cb.coalesce(order.<String>get("providerOrderId"), "")), pattern, '\\'),
						NexHomeSearch.personMatches(cb, order.get("customer"), pattern), cb.exists(bookings));
			});
		}
		return spec;
	}

	Optional<NexHomePaymentOrder> findByIdempotencyKey(String idempotencyKey);

	Optional<NexHomePaymentOrder> findByProviderAndProviderOrderId(String provider, String providerOrderId);

	List<NexHomePaymentOrder> findByCheckoutIdOrderByIdDesc(UUID checkoutId);

	long countByPaymentStatusIn(List<String> statuses);

	/** Newest first, with the customers. */
	@EntityGraph(attributePaths = "customer")
	Page<NexHomePaymentOrder> findAllByOrderByCreationDateDescIdDesc(Pageable pageable);
}
