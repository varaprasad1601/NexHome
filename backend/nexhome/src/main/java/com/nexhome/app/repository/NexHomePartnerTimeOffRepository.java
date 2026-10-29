package com.nexhome.app.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerTimeOff;

@Repository
public interface NexHomePartnerTimeOffRepository extends JpaRepository<NexHomePartnerTimeOff, Long> {

	/** All of a partner's time off, latest first. */
	List<NexHomePartnerTimeOff> findByPartnerIdOrderByStartsAtDesc(Long partnerId);

	/** Time off not over yet, soonest first. */
	List<NexHomePartnerTimeOff> findByPartnerIdAndEndsAtAfterOrderByStartsAtAsc(Long partnerId, Instant now);

	/**
	 * Whether the partner has time off (not cancelled) overlapping [startsAt, endsAt), other than {@code excludeId}
	 * (-1: none).
	 */
	@Query("select count(t) > 0 from NexHomePartnerTimeOff t where t.partner.id = :partnerId and t.id <> :excludeId "
			+ "and t.cancelledAt is null and t.startsAt < :endsAt and t.endsAt > :startsAt")
	boolean existsOverlapping(@Param("partnerId") Long partnerId, @Param("startsAt") Instant startsAt,
			@Param("endsAt") Instant endsAt, @Param("excludeId") Long excludeId);

	/** The ids of the partners (of {@code partnerIds}) with time off (not cancelled) overlapping [startsAt, endsAt). */
	@Query("select distinct t.partner.id from NexHomePartnerTimeOff t where t.partner.id in :partnerIds "
			+ "and t.cancelledAt is null and t.startsAt < :endsAt and t.endsAt > :startsAt")
	List<Long> findPartnersOffBetween(@Param("partnerIds") Collection<Long> partnerIds,
			@Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt);

	/** How many partners have time off (not cancelled) overlapping [startsAt, endsAt). */
	@Query("select count(distinct t.partner.id) from NexHomePartnerTimeOff t where t.cancelledAt is null "
			+ "and t.startsAt < :endsAt and t.endsAt > :startsAt")
	long countPartnersOffBetween(@Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt);
}
