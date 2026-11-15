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

	/**
	 * The partner tapped Finish work on an unpaid ONLINE job (V70): the customer is asked to confirm the work is
	 * done; the work keeps running until they do. The assigned partner or BOOKING_MANAGE write.
	 */
	NexHomeBookingDetailResponse requestCompletion(Long bookingId);

	/**
	 * The partner reached the work area (V75), after accepting and before starting: the arrival time and a history
	 * line are kept, the customer is told (PARTNER_ARRIVED: the doorbell). Again: nothing changes. The assigned partner
	 * or BOOKING_MANAGE write.
	 */
	NexHomeBookingDetailResponse markArrived(Long bookingId);

	/**
	 * The booking's customer answers it: not done clears the request (the partner carries on); done stops the work
	 * (an HOURLY job priced for the minutes worked) and completes the booking with its payment PENDING.
	 */
	NexHomeBookingDetailResponse answerCompletion(Long bookingId, boolean completed);

	/**
	 * Staff (BOOKING_MANAGE write) complete a job whose work is finished (IN_PROGRESS with its end time) but is not
	 * paid: assignment and booking COMPLETED, the payment staying PENDING; the customer and partner told.
	 */
	NexHomeBookingDetailResponse completeJob(Long bookingId);

	NexHomePartnerLocationResponse updateLocation(NexHomePartnerLocationUpdateRequest request);

	/** The partner's latest position for the booking, or null; visible like the booking. */
	NexHomePartnerLocationResponse getLatestLocation(Long bookingId);

	/** Offers that ran out become EXPIRED (run on a schedule); returns how many. */
	int expireOffers();

	/**
	 * Warns the staff who handle bookings, once per booking, of CONFIRMED bookings starting within 2 hours without a
	 * partner (scheduled job); returns how many.
	 */
	int alertUnassignedBookings();
}
