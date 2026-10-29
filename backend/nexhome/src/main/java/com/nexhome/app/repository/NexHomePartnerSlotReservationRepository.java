package com.nexhome.app.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerSlotReservation;

@Repository
public interface NexHomePartnerSlotReservationRepository extends JpaRepository<NexHomePartnerSlotReservation, Long> {

	/** A partner's reservations overlapping [from, to), earliest first. */
	List<NexHomePartnerSlotReservation> findByPartnerIdAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(
			Long partnerId, Instant from, Instant to);

	/** A booking's reservations in the given statuses. */
	List<NexHomePartnerSlotReservation> findByBookingIdAndReservationStatusIn(Long bookingId, Collection<String> statuses);

	/** Whether the partner has a CONFIRMED reservation, or a hold still running, overlapping [startsAt, endsAt). */
	@Query("select count(r) > 0 from NexHomePartnerSlotReservation r where r.partner.id = :partnerId "
			+ "and (r.reservationStatus = 'CONFIRMED' or (r.reservationStatus = 'HELD' and r.holdExpiresAt > :now)) "
			+ "and r.startsAt < :endsAt and r.endsAt > :startsAt")
	boolean existsActiveOverlapping(@Param("partnerId") Long partnerId, @Param("startsAt") Instant startsAt,
			@Param("endsAt") Instant endsAt, @Param("now") Instant now);

	/** The ids of the partners (of {@code partnerIds}) booked, or held by a running hold, during [startsAt, endsAt). */
	@Query("select distinct r.partner.id from NexHomePartnerSlotReservation r where r.partner.id in :partnerIds "
			+ "and (r.reservationStatus = 'CONFIRMED' or (r.reservationStatus = 'HELD' and r.holdExpiresAt > :now)) "
			+ "and r.startsAt < :endsAt and r.endsAt > :startsAt")
	List<Long> findPartnersReservedBetween(@Param("partnerIds") Collection<Long> partnerIds,
			@Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt, @Param("now") Instant now);

	/** Ends the holds that ran out (status EXPIRED); returns how many. */
	// No clearing: a caller's pending changes to other entities must survive
	@Modifying(flushAutomatically = true)
	@Query("update NexHomePartnerSlotReservation r set r.reservationStatus = 'EXPIRED', r.updatedDate = :now "
			+ "where r.reservationStatus = 'HELD' and r.holdExpiresAt <= :now")
	int expireHolds(@Param("now") Instant now);
}
