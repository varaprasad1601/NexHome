package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeBookingStatusHistory;

@Repository
public interface NexHomeBookingStatusHistoryRepository extends JpaRepository<NexHomeBookingStatusHistory, Long> {

	/** The CANCELLED rows of these bookings: [booking id, changed by, when]. */
	@org.springframework.data.jpa.repository.Query("select h.booking.id, h.changedBy, h.creationDate "
			+ "from NexHomeBookingStatusHistory h where h.newStatus = 'CANCELLED' and h.booking.id in :bookingIds")
	List<Object[]> cancellationsOf(@org.springframework.data.repository.query.Param("bookingIds")
			java.util.Collection<Long> bookingIds);

	/** Oldest first. */
	List<NexHomeBookingStatusHistory> findByBookingIdOrderByCreationDateAscIdAsc(Long bookingId);
}
