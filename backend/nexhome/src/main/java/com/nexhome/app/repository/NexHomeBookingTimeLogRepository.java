package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeBookingTimeLog;

@Repository
public interface NexHomeBookingTimeLogRepository extends JpaRepository<NexHomeBookingTimeLog, Long> {

	/** In the order they happened. */
	List<NexHomeBookingTimeLog> findByBookingIdOrderByEventAtAscIdAsc(Long bookingId);
}
