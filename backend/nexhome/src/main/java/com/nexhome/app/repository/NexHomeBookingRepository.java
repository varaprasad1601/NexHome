package com.nexhome.app.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeBooking;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

@Repository
public interface NexHomeBookingRepository extends JpaRepository<NexHomeBooking, Long>,
		JpaSpecificationExecutor<NexHomeBooking> {

	/** A page of bookings, with the customers and partners in the same query (their names are listed). */
	@Override
	@EntityGraph(attributePaths = { "customer", "customer.user", "partner" })
	Page<NexHomeBooking> findAll(Specification<NexHomeBooking> spec, Pageable pageable);

	boolean existsByAddressId(Long addressId);

	/** The bookings of a checkout. */
	List<NexHomeBooking> findByCheckoutIdOrderByIdAsc(UUID checkoutId);

	/** How many bookings are in each status: [status, count]. */
	@Query("select b.bookingStatus, count(b) from NexHomeBooking b group by b.bookingStatus")
	List<Object[]> countByStatus();

	/** Bookings scheduled to start in [from, to). */
	/** The partners (of {@code partnerIds}) with a booking in {@code statuses} overlapping [startsAt, endsAt). */
	@Query("select distinct b.partner.id from NexHomeBooking b where b.partner.id in :partnerIds "
			+ "and b.bookingStatus in :statuses and b.scheduledStartAt < :endsAt and b.scheduledEndAt > :startsAt")
	List<Long> findPartnersBookedBetween(@Param("partnerIds") java.util.Collection<Long> partnerIds,
			@Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt,
			@Param("statuses") java.util.Collection<String> statuses);

	/** Whether the partner has a booking in {@code statuses} overlapping [startsAt, endsAt), other than one. */
	@Query("select count(b) > 0 from NexHomeBooking b where b.partner.id = :partnerId and b.id <> :excludeId "
			+ "and b.bookingStatus in :statuses and b.scheduledStartAt < :endsAt and b.scheduledEndAt > :startsAt")
	boolean existsPartnerBookedBetween(@Param("partnerId") Long partnerId, @Param("startsAt") Instant startsAt,
			@Param("endsAt") Instant endsAt, @Param("statuses") java.util.Collection<String> statuses,
			@Param("excludeId") Long excludeId);

	/**
	 * A partner's bookings by status and the status of the assignment holding them (null without one):
	 * [booking status, assignment status, count]. A booking has at most one holding assignment (V36's index).
	 */
	@Query("select b.bookingStatus, a.assignmentStatus, count(b) from NexHomeBooking b "
			+ "left join NexHomeBookingAssignment a on a.booking = b "
			+ "and a.assignmentStatus in ('ASSIGNED', 'ACCEPTED', 'STARTED', 'COMPLETED') "
			+ "where b.partner.id = :partnerId group by b.bookingStatus, a.assignmentStatus")
	List<Object[]> countStagesOfPartner(@Param("partnerId") Long partnerId);

	/** The same for a customer's bookings: [booking status, assignment status, count]. */
	@Query("select b.bookingStatus, a.assignmentStatus, count(b) from NexHomeBooking b "
			+ "left join NexHomeBookingAssignment a on a.booking = b "
			+ "and a.assignmentStatus in ('ASSIGNED', 'ACCEPTED', 'STARTED', 'COMPLETED') "
			+ "where b.customer.id = :customerId group by b.bookingStatus, a.assignmentStatus")
	List<Object[]> countStagesOfCustomer(@Param("customerId") Long customerId);

	/** How many bookings were made in each month (IST), oldest first; months without any are left out. */
	@Query(nativeQuery = true, value = """
			select to_char(b.creation_date at time zone 'Asia/Kolkata', 'YYYY-MM') as month, count(*) as total
			from nexhome_bookings b
			group by 1
			order by 1""")
	List<MonthlyCount> countByMonth();

	/** A partner's bookings in a status completed since {@code from}, latest first, with their services. */
	@EntityGraph(attributePaths = "service")
	List<NexHomeBooking> findByPartnerIdAndBookingStatusAndActualEndAtGreaterThanEqualOrderByActualEndAtDescIdDesc(
			Long partnerId, String bookingStatus, Instant from);

	/** All of a partner's bookings in a status, latest completed first, with their services. */
	@EntityGraph(attributePaths = "service")
	List<NexHomeBooking> findByPartnerIdAndBookingStatusOrderByActualEndAtDescIdDesc(Long partnerId,
			String bookingStatus);

	/** One row of {@link #countByMonth}. */
	interface MonthlyCount {

		/** {@code YYYY-MM}. */
		String getMonth();

		Long getTotal();
	}

	@Query("select count(b) from NexHomeBooking b where b.scheduledStartAt >= :from and b.scheduledStartAt < :to")
	long countScheduledBetween(@Param("from") Instant from, @Param("to") Instant to);

	/**
	 * The console's booking list: only the filters given (no null checks in JPQL, see CLAUDE.md). {@code search} is
	 * trimmed and lower-cased; it matches the public id, the service name, and the customer's or partner's names or
	 * public ids. {@code from} / {@code to} bound the scheduled start.
	 */
	static Specification<NexHomeBooking> bookingSpec(String bookingStatus, String paymentStatus, Long customerId,
			Long partnerId, Long serviceId, Instant from, Instant to, String search) {
		Specification<NexHomeBooking> spec = (booking, query, cb) -> cb.conjunction();
		if (bookingStatus != null) {
			spec = spec.and((booking, query, cb) -> cb.equal(booking.get("bookingStatus"), bookingStatus));
		}
		if (paymentStatus != null) {
			spec = spec.and((booking, query, cb) -> cb.equal(booking.get("paymentStatus"), paymentStatus));
		}
		if (customerId != null) {
			spec = spec.and((booking, query, cb) -> cb.equal(booking.get("customer").get("id"), customerId));
		}
		if (partnerId != null) {
			spec = spec.and((booking, query, cb) -> cb.equal(booking.get("partner").get("id"), partnerId));
		}
		if (serviceId != null) {
			spec = spec.and((booking, query, cb) -> cb.equal(booking.get("service").get("id"), serviceId));
		}
		if (from != null) {
			spec = spec.and((booking, query, cb) -> cb.greaterThanOrEqualTo(booking.get("scheduledStartAt"), from));
		}
		if (to != null) {
			spec = spec.and((booking, query, cb) -> cb.lessThan(booking.get("scheduledStartAt"), to));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((booking, query, cb) -> {
				Join<Object, Object> customer = booking.join("customer", JoinType.LEFT);
				Join<Object, Object> partner = booking.join("partner", JoinType.LEFT);
				return cb.or(
						cb.like(cb.lower(booking.get("publicId")), pattern, '\\'),
						cb.like(cb.lower(booking.get("serviceNameSnapshot")), pattern, '\\'),
						cb.like(cb.lower(customer.get("publicId")), pattern, '\\'),
						cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(customer.<String>get("firstName"), ""), " "),
								cb.coalesce(customer.<String>get("lastName"), ""))), pattern, '\\'),
						cb.like(cb.lower(partner.get("publicId")), pattern, '\\'),
						cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(partner.<String>get("firstName"), ""), " "),
								cb.coalesce(partner.<String>get("lastName"), ""))), pattern, '\\'));
			});
		}
		return spec;
	}
}
