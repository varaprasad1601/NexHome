package com.nexhome.app.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
import com.nexhome.app.service.NexHomeBookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Module 6: bookings, their assignment to partners, the service timer and live location. Staff endpoints need
 * BOOKING_MANAGE; the customer's and partner's own actions are checked in the service.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class NexHomeBookingController {

	private final NexHomeBookingService bookingService;

	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('BOOKING_MANAGE', 'WRITE')")
	public NexHomeBookingResponse createBooking(@Valid @RequestBody NexHomeBookingCreateRequest request) {
		return bookingService.createBooking(request);
	}

	/** Query parameters (all optional): page, size, bookingStatus, paymentStatus, customerProfileId,
	 * partnerProfileId, serviceId, from / to (scheduled start, ISO-8601 instants), search. */
	@GetMapping
	@PreAuthorize("@permissions.can('BOOKING_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeBookingResponse> getBookings(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String bookingStatus,
			@RequestParam(required = false) String paymentStatus,
			@RequestParam(required = false) Long customerProfileId,
			@RequestParam(required = false) Long partnerProfileId, @RequestParam(required = false) Long serviceId,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(required = false) String search) {
		return bookingService.getBookings(page, size, bookingStatus, paymentStatus, customerProfileId,
				partnerProfileId, serviceId, from, to, search);
	}

	@GetMapping("/stats")
	@PreAuthorize("@permissions.can('BOOKING_MANAGE', 'READ')")
	public NexHomeBookingStatsResponse getBookingStats() {
		return bookingService.getBookingStats();
	}

	/** The customer's own figures, or BOOKING_MANAGE read (checked in the service). */
	@GetMapping("/customer-stats/{customerProfileId}")
	public NexHomeCustomerBookingStatsResponse getCustomerBookingStats(@PathVariable Long customerProfileId) {
		return bookingService.getCustomerBookingStats(customerProfileId);
	}

	/** The partner's own figures, or BOOKING_MANAGE read (checked in the service). */
	@GetMapping("/partner-stats/{partnerProfileId}")
	public NexHomePartnerJobStatsResponse getPartnerJobStats(@PathVariable Long partnerProfileId) {
		return bookingService.getPartnerJobStats(partnerProfileId);
	}

	@GetMapping("/me")
	public NexHomePageResponse<NexHomeBookingResponse> getMyCustomerBookings(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return bookingService.getMyCustomerBookings(page, size);
	}

	@GetMapping("/partner/me")
	public NexHomePageResponse<NexHomeBookingResponse> getMyPartnerBookings(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return bookingService.getMyPartnerBookings(page, size);
	}

	@GetMapping("/offers/me")
	public NexHomePageResponse<NexHomeBookingAssignmentResponse> getMyOffers(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return bookingService.getMyOffers(page, size);
	}

	@GetMapping("/{bookingId}")
	public NexHomeBookingDetailResponse getBooking(@PathVariable Long bookingId) {
		return bookingService.getBooking(bookingId);
	}

	@PutMapping("/status/update")
	public NexHomeBookingResponse updateBookingStatus(@Valid @RequestBody NexHomeBookingStatusUpdateRequest request) {
		return bookingService.updateBookingStatus(request);
	}

	@PutMapping("/cancel")
	public NexHomeBookingResponse cancelBooking(@Valid @RequestBody NexHomeBookingCancelRequest request) {
		return bookingService.cancelBooking(request);
	}

	@PostMapping("/assignments/offer")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('BOOKING_MANAGE', 'WRITE')")
	public NexHomeBookingAssignmentResponse offerBooking(@Valid @RequestBody NexHomeAssignmentOfferRequest request) {
		return bookingService.offerBooking(request);
	}

	@PutMapping("/assignments/respond")
	public NexHomeBookingAssignmentResponse respondToOffer(
			@Valid @RequestBody NexHomeAssignmentRespondRequest request) {
		return bookingService.respondToOffer(request);
	}

	@PutMapping("/assignments/cancel")
	@PreAuthorize("@permissions.can('BOOKING_MANAGE', 'WRITE')")
	public NexHomeBookingAssignmentResponse cancelAssignment(
			@Valid @RequestBody NexHomeAssignmentCancelRequest request) {
		return bookingService.cancelAssignment(request);
	}

	@PostMapping("/timer")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeBookingDetailResponse recordTimerEvent(@Valid @RequestBody NexHomeBookingTimerRequest request) {
		return bookingService.recordTimerEvent(request);
	}

	@PostMapping("/location")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomePartnerLocationResponse updateLocation(
			@Valid @RequestBody NexHomePartnerLocationUpdateRequest request) {
		return bookingService.updateLocation(request);
	}

	@GetMapping("/location/{bookingId}")
	public NexHomePartnerLocationResponse getLatestLocation(@PathVariable Long bookingId) {
		return bookingService.getLatestLocation(bookingId);
	}
}
