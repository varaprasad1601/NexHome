package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerLocationUpdate;

@Repository
public interface NexHomePartnerLocationUpdateRepository extends JpaRepository<NexHomePartnerLocationUpdate, Long> {

	/** The latest position for a booking. */
	Optional<NexHomePartnerLocationUpdate> findFirstByBookingIdOrderByRecordedAtDescIdDesc(Long bookingId);
}
