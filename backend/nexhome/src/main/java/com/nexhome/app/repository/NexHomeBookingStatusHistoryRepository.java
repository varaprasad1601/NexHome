package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeBookingStatusHistory;

@Repository
public interface NexHomeBookingStatusHistoryRepository extends JpaRepository<NexHomeBookingStatusHistory, Long> {

	/** Oldest first. */
	List<NexHomeBookingStatusHistory> findByBookingIdOrderByCreationDateAscIdAsc(Long bookingId);
}
