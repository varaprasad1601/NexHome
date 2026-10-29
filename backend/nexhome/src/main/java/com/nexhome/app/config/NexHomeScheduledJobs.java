package com.nexhome.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.nexhome.app.service.NexHomeBookingService;

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

	/** Booking offers that ran out become EXPIRED (every minute by default; nexhome.slots.expire-interval-ms). */
	@Scheduled(fixedDelayString = "${nexhome.slots.expire-interval-ms:60000}", initialDelay = 35000)
	public void expireBookingOffers() {
		bookingService.expireOffers();
	}
}
