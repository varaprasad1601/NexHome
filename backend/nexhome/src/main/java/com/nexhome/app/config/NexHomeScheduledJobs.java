package com.nexhome.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.nexhome.app.service.NexHomeBookingService;
import com.nexhome.app.service.NexHomeFileService;

import lombok.RequiredArgsConstructor;

/**
 * Maintenance that runs on a timer. Each job is a single idempotent update, so running it on several instances at once
 * is safe.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class NexHomeScheduledJobs {

	private final NexHomeBookingService bookingService;
	private final NexHomeFileService fileService;

	/** Booking offers that ran out become EXPIRED (every minute by default; nexhome.slots.expire-interval-ms). */
	@Scheduled(fixedDelayString = "${nexhome.slots.expire-interval-ms:60000}", initialDelay = 35000)
	public void expireBookingOffers() {
		bookingService.expireOffers();
	}

	/** CONFIRMED bookings starting within 2 hours without a partner: staff are warned once (every 5 minutes). */
	@Scheduled(fixedDelayString = "${nexhome.bookings.unassigned-alert-interval-ms:300000}", initialDelay = 45000)
	public void alertUnassignedBookings() {
		bookingService.alertUnassignedBookings();
	}

	/** Uploads never given to anything within a day are removed (every hour; nexhome.files.cleanup-interval-ms). */
	@Scheduled(fixedDelayString = "${nexhome.files.cleanup-interval-ms:3600000}", initialDelay = 120000)
	public void removeUnattachedUploads() {
		fileService.removeUnattachedUploads();
	}
}
