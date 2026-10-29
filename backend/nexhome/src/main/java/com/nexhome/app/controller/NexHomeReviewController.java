package com.nexhome.app.controller;

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

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeReviewCreateRequest;
import com.nexhome.app.dto.NexHomeReviewResponse;
import com.nexhome.app.dto.NexHomeReviewStatsResponse;
import com.nexhome.app.dto.NexHomeReviewStatusUpdateRequest;
import com.nexhome.app.service.NexHomeReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Module 9, reviews: customers rate completed bookings (the service and the partner); staff with REVIEW_MANAGE see
 * and moderate them. Owner rules are checked in the service.
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class NexHomeReviewController {

	private final NexHomeReviewService reviewService;

	/** The booking's customer, or REVIEW_MANAGE write (checked in the service). */
	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeReviewResponse createReview(@Valid @RequestBody NexHomeReviewCreateRequest request) {
		return reviewService.createReview(request);
	}

	@PutMapping("/status/update")
	@PreAuthorize("@permissions.can('REVIEW_MANAGE', 'WRITE')")
	public NexHomeReviewResponse updateReviewStatus(@Valid @RequestBody NexHomeReviewStatusUpdateRequest request) {
		return reviewService.updateReviewStatus(request);
	}

	@GetMapping
	@PreAuthorize("@permissions.can('REVIEW_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeReviewResponse> getReviews(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String reviewStatus,
			@RequestParam(required = false) Long partnerProfileId,
			@RequestParam(required = false) Long customerProfileId, @RequestParam(required = false) Integer rating,
			@RequestParam(required = false) String search) {
		return reviewService.getReviews(page, size, reviewStatus, partnerProfileId, customerProfileId, rating, search);
	}

	@GetMapping("/stats")
	@PreAuthorize("@permissions.can('REVIEW_MANAGE', 'READ')")
	public NexHomeReviewStatsResponse getStats() {
		return reviewService.getStats();
	}

	/** One partner's or customer's reviews at a glance (the owner or REVIEW_MANAGE read, checked in the service). */
	@GetMapping("/summary")
	public com.nexhome.app.dto.NexHomeReviewSummaryResponse getSummary(
			@RequestParam(required = false) Long partnerProfileId,
			@RequestParam(required = false) Long customerProfileId) {
		return reviewService.getSummary(partnerProfileId, customerProfileId);
	}

	/** About the caller as a partner (published), else written by them as a customer. */
	@GetMapping("/me")
	public NexHomePageResponse<NexHomeReviewResponse> getMyReviews(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return reviewService.getMyReviews(page, size);
	}

	/** Its customer and partner, or BOOKING_MANAGE / REVIEW_MANAGE read (checked in the service). */
	@GetMapping("/bookings/{bookingId}")
	public NexHomeReviewResponse getReviewOfBooking(@PathVariable Long bookingId) {
		return reviewService.getReviewOfBooking(bookingId);
	}
}
