package com.nexhome.app.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeBookingAssignment;

@Repository
public interface NexHomeBookingAssignmentRepository extends JpaRepository<NexHomeBookingAssignment, Long> {

	/** How many of a partner's assignments are in a status (e.g. REJECTED: offers and assignments declined). */
	@Query("select count(a) from NexHomeBookingAssignment a where a.partner.id = :partnerId "
			+ "and a.assignmentStatus = :status")
	long countOfPartnerByStatus(@Param("partnerId") Long partnerId, @Param("status") String status);

	/** How many assignments of a customer's bookings are in a status (e.g. REJECTED: declined by partners). */
	@Query("select count(a) from NexHomeBookingAssignment a where a.booking.customer.id = :customerId "
			+ "and a.assignmentStatus = :status")
	long countOfCustomerByStatus(@Param("customerId") Long customerId, @Param("status") String status);

	/** Newest first, with the partners in the same query. */
	@EntityGraph(attributePaths = "partner")
	List<NexHomeBookingAssignment> findByBookingIdOrderByOfferedAtDescIdDesc(Long bookingId);

	List<NexHomeBookingAssignment> findByBookingIdAndAssignmentStatus(Long bookingId, String assignmentStatus);

	/** The booking's accepted assignment; unique by ux_nexhome_booking_assignments_one_accepted. */
	Optional<NexHomeBookingAssignment> findFirstByBookingIdAndAssignmentStatusIn(Long bookingId,
			List<String> assignmentStatuses);

	boolean existsByBookingIdAndPartnerIdAndAssignmentStatus(Long bookingId, Long partnerId, String assignmentStatus);

	/** A partner's offers (and other assignments) by status, newest first, with the bookings. */
	@EntityGraph(attributePaths = "booking")
	Page<NexHomeBookingAssignment> findByPartnerIdAndAssignmentStatusOrderByOfferedAtDesc(Long partnerId,
			String assignmentStatus, Pageable pageable);

	/** Offers that ran out. */
	List<NexHomeBookingAssignment> findByAssignmentStatusAndExpiresAtLessThanEqual(String assignmentStatus,
			Instant now);
}
