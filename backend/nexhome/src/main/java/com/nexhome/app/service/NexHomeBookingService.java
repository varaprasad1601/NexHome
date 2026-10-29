package com.nexhome.app.service;

import java.time.Instant;

import com.nexhome.app.dto.NexHomeAssignmentCancelRequest;
import com.nexhome.app.dto.NexHomeAssignmentOfferRequest;
import com.nexhome.app.dto.NexHomeAssignmentRespondRequest;
import com.nexhome.app.dto.NexHomeBookingAssignmentResponse;
import com.nexhome.app.dto.NexHomeBookingCancelRequest;
import com.nexhome.app.dto.NexHomeBookingCreateRequest;
import com.nexhome.app.dto.NexHomeBookingDetailResponse;
import com.nexhome.app.dto.NexHomeBookingResponse;
import com.nexhome.app.dto.NexHomeBookingStatsResponse;
import com.nexhome.app.dto.NexHomeBookingStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeBookingTimerRequest;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeCustomerBookingStatsResponse;
import com.nexhome.app.dto.NexHomePartnerJobStatsResponse;
import com.nexhome.app.dto.NexHomePartnerLocationResponse;
import com.nexhome.app.dto.NexHomePartnerLocationUpdateRequest;

/**
 * Module 6: bookings after checkout. Staff (BOOKING_MANAGE) see and run every booking; the customer sees and cancels
 * their own; the assigned partner answers offers, reports the trip, runs the service timer and shares their location.
 */
public interface NexHomeBookingService {

	/** Staff book a service for a customer. */
	NexHomeBookingResponse createBooking(NexHomeBookingCreateRequest request);

	/** The console's list (BOOKING_MANAGE read), newest first, with optional filters. */
	NexHomePageResponse<NexHomeBookingResponse> getBookings(Integer page, Integer size, String bookingStatus,
			String paymentStatus, Long customerProfileId, Long partnerProfileId, Long serviceId, Instant from,
			Instant to, String search);

	NexHomeBookingStatsResponse getBookingStats();

	/** A partner's jobs and earnings (the partner, or BOOKING_MANAGE read; earnings need PAYOUT_MANAGE read). */
	NexHomePartnerJobStatsResponse getPartnerJobStats(Long partnerProfileId);

	/** A customer's bookings (total, completed, cancelled) and spend: the customer, or BOOKING_MANAGE read. */
	NexHomeCustomerBookingStatsResponse getCustomerBookingStats(Long customerProfileId);

	/** The signed-in customer's bookings, newest first. */
	NexHomePageResponse<NexHomeBookingResponse> getMyCustomerBookings(Integer page, Integer size);

	/** The signed-in partner's bookings, newest first. */
	NexHomePageResponse<NexHomeBookingResponse> getMyPartnerBookings(Integer page, Integer size);

	/** The signed-in partner's open offers, newest first. */
	NexHomePageResponse<NexHomeBookingAssignmentResponse> getMyOffers(Integer page, Integer size);

	/** Its customer, its partner (or one it is offered to) or BOOKING_MANAGE read. */
	NexHomeBookingDetailResponse getBooking(Long bookingId);

	NexHomeBookingResponse updateBookingStatus(NexHomeBookingStatusUpdateRequest request);

	NexHomeBookingResponse cancelBooking(NexHomeBookingCancelRequest request);

	NexHomeBookingAssignmentResponse offerBooking(NexHomeAssignmentOfferRequest request);

	NexHomeBookingAssignmentResponse respondToOffer(NexHomeAssignmentRespondRequest request);

	NexHomeBookingAssignmentResponse cancelAssignment(NexHomeAssignmentCancelRequest request);

	NexHomeBookingDetailResponse recordTimerEvent(NexHomeBookingTimerRequest request);

	NexHomePartnerLocationResponse updateLocation(NexHomePartnerLocationUpdateRequest request);

	/** The partner's latest position for the booking, or null; visible like the booking. */
	NexHomePartnerLocationResponse getLatestLocation(Long bookingId);

	/** Offers that ran out become EXPIRED (run on a schedule); returns how many. */
	int expireOffers();
}
