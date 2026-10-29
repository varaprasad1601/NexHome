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

import com.nexhome.app.model.NexHomePaymentRefund;

@Repository
public interface NexHomePaymentRefundRepository extends JpaRepository<NexHomePaymentRefund, Long> {

	List<NexHomePaymentRefund> findByBookingIdOrderByIdDesc(Long bookingId);

	/** Money refunded: [sum, count] of SUCCEEDED refunds. */
	@Query("select coalesce(sum(r.amount), 0), count(r) from NexHomePaymentRefund r where r.status = 'SUCCEEDED'")
	List<Object[]> succeededTotals();

	/** What was refunded (SUCCEEDED) to a customer for their bookings. */
	@Query("select coalesce(sum(r.amount), 0) from NexHomePaymentRefund r where r.booking.customer.id = :customerId "
			+ "and r.status = 'SUCCEEDED'")
	java.math.BigDecimal sumSucceededOfCustomer(@Param("customerId") Long customerId);

	/** What has been refunded (or is being refunded) from a payment. */
	@Query("select coalesce(sum(r.amount), 0) from NexHomePaymentRefund r where r.payment.id = :paymentId "
			+ "and r.status in ('REQUESTED', 'PROCESSING', 'SUCCEEDED')")
	BigDecimal sumOpenOrSucceeded(@Param("paymentId") Long paymentId);

	/** Newest first, with the bookings. */
	@EntityGraph(attributePaths = { "booking", "booking.customer" })
	Page<NexHomePaymentRefund> findAllByOrderByCreationDateDescIdDesc(Pageable pageable);

	@EntityGraph(attributePaths = { "booking", "booking.customer" })
	Page<NexHomePaymentRefund> findByStatusOrderByCreationDateDescIdDesc(String status, Pageable pageable);
}
