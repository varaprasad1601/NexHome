package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeCashCollection;

@Repository
public interface NexHomeCashCollectionRepository extends JpaRepository<NexHomeCashCollection, Long> {

	Optional<NexHomeCashCollection> findByBookingId(Long bookingId);

	java.util.List<NexHomeCashCollection> findByBookingIdIn(java.util.Collection<Long> bookingIds);

	/** Cash still to collect: [sum, count] of DUE collections. */
	@org.springframework.data.jpa.repository.Query("select coalesce(sum(c.amount), 0), count(c) "
			+ "from NexHomeCashCollection c where c.status = 'DUE'")
	java.util.List<Object[]> dueTotals();

	/** The cash collected (COLLECTED) from a customer for their bookings. */
	@org.springframework.data.jpa.repository.Query("select coalesce(sum(c.amount), 0) from NexHomeCashCollection c "
			+ "where c.booking.customer.id = :customerId and c.status = 'COLLECTED'")
	java.math.BigDecimal sumCollectedFromCustomer(
			@org.springframework.data.repository.query.Param("customerId") Long customerId);

	/** A partner's COLLECTED cash with each booking's service (its commission is the platform's share). */
	@org.springframework.data.jpa.repository.Query("select c from NexHomeCashCollection c join fetch c.booking b "
			+ "join fetch b.service where b.partner.id = :partnerId and c.status = 'COLLECTED'")
	java.util.List<NexHomeCashCollection> findCollectedByPartnerWithService(
			@org.springframework.data.repository.query.Param("partnerId") Long partnerId);

	/** Newest first, with the bookings, their customers and partners. */
	@EntityGraph(attributePaths = { "booking", "booking.customer", "booking.partner" })
	Page<NexHomeCashCollection> findAllByOrderByCreationDateDescIdDesc(Pageable pageable);

	@EntityGraph(attributePaths = { "booking", "booking.customer", "booking.partner" })
	Page<NexHomeCashCollection> findByStatusOrderByCreationDateDescIdDesc(String status, Pageable pageable);
}
