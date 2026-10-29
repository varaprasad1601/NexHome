package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeBookingItem;

@Repository
public interface NexHomeBookingItemRepository extends JpaRepository<NexHomeBookingItem, Long> {

	List<NexHomeBookingItem> findByBookingIdOrderByIdAsc(Long bookingId);
}
