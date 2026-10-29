package com.nexhome.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.nexhome.app.model.NexHomePayment;

@Repository
public interface NexHomePaymentRepository extends JpaRepository<NexHomePayment, Long>,
		JpaSpecificationExecutor<NexHomePayment> {

	/** A page of payments with their bookings and customers (for the list). */
	@Override
	@EntityGraph(attributePaths = { "booking", "booking.customer", "paymentOrder" })
	Page<NexHomePayment> findAll(org.springframework.data.jpa.domain.Specification<NexHomePayment> spec,
			Pageable pageable);

	List<NexHomePayment> findByBookingIdOrderByIdDesc(Long bookingId);

	List<NexHomePayment> findByPaymentOrderIdOrderByIdAsc(Long paymentOrderId);

	/** The booking's captured payment (or a partly refunded one). */
	Optional<NexHomePayment> findFirstByBookingIdAndStatusInOrderByIdDesc(Long bookingId, Collection<String> statuses);

	/** Money captured (refunded or not): [sum, count]. */
	@Query("select coalesce(sum(p.amount), 0), count(p) from NexHomePayment p "
			+ "where p.status in ('CAPTURED', 'PARTIALLY_REFUNDED', 'REFUNDED')")
	List<Object[]> capturedTotals();

	/** What a customer paid online for their bookings (captured, refunded or not). */
	@Query("select coalesce(sum(p.amount), 0) from NexHomePayment p where p.booking.customer.id = :customerId "
			+ "and p.status in ('CAPTURED', 'PARTIALLY_REFUNDED', 'REFUNDED')")
	java.math.BigDecimal sumCapturedOfCustomer(@Param("customerId") Long customerId);

	/**
	 * Revenue per month (IST), oldest first; months without any are left out: payments captured (by
	 * {@code captured_at}) plus cash collected (by {@code confirmed_at}) less refunds that succeeded (by
	 * {@code completed_at}, else when they were made).
	 */
	@Query(nativeQuery = true, value = """
			select m.month as month, sum(m.amount) as total
			from (
				select to_char(p.captured_at at time zone 'Asia/Kolkata', 'YYYY-MM') as month, p.amount
				from nexhome_payments p
				where p.status in ('CAPTURED', 'PARTIALLY_REFUNDED', 'REFUNDED') and p.captured_at is not null
				union all
				select to_char(coalesce(r.completed_at, r.creation_date) at time zone 'Asia/Kolkata', 'YYYY-MM'),
					-r.amount
				from nexhome_payment_refunds r
				where r.status = 'SUCCEEDED'
				union all
				select to_char(c.confirmed_at at time zone 'Asia/Kolkata', 'YYYY-MM'), c.amount
				from nexhome_cash_collections c
				where c.status = 'COLLECTED' and c.confirmed_at is not null
			) m
			group by 1
			order by 1""")
	List<MonthlyAmount> revenueByMonth();

	/** One row of {@link #revenueByMonth}. */
	interface MonthlyAmount {

		/** {@code YYYY-MM}. */
		String getMonth();

		java.math.BigDecimal getTotal();
	}

	/**
	 * The console's payment list: only the filters given (no null checks in JPQL). {@code search} (trimmed, lower
	 * case) matches the booking id, the order id and the provider's payment reference.
	 */
	static org.springframework.data.jpa.domain.Specification<NexHomePayment> paymentSpec(String status,
			Long customerId, String search) {
		org.springframework.data.jpa.domain.Specification<NexHomePayment> spec = (payment, query, cb) -> cb
				.conjunction();
		if (status != null) {
			spec = spec.and((payment, query, cb) -> cb.equal(payment.get("status"), status));
		}
		if (customerId != null) {
			spec = spec.and((payment, query, cb) -> cb.equal(payment.get("booking").get("customer").get("id"),
					customerId));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((payment, query, cb) -> cb.or(
					cb.like(cb.lower(payment.get("booking").get("publicId")), pattern, '\\'),
					cb.like(cb.lower(payment.get("paymentOrder").get("publicId")), pattern, '\\'),
					cb.like(cb.lower(cb.coalesce(payment.<String>get("providerPaymentId"), "")), pattern, '\\')));
		}
		return spec;
	}

	/** The payment, locked until the transaction ends (refunds are counted against it one at a time). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from NexHomePayment p where p.id = :id")
	Optional<NexHomePayment> findByIdForUpdate(@Param("id") Long id);
}
