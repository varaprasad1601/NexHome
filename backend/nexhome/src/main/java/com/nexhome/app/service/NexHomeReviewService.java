package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeReviewCreateRequest;
import com.nexhome.app.dto.NexHomeReviewResponse;
import com.nexhome.app.dto.NexHomeReviewStatsResponse;
import com.nexhome.app.dto.NexHomeReviewStatusUpdateRequest;

/** Module 9: customers' reviews of completed bookings, and their moderation. */
public interface NexHomeReviewService {

	/** The booking's customer, or REVIEW_MANAGE write on their behalf: a COMPLETED booking with a partner, once. */
	NexHomeReviewResponse createReview(NexHomeReviewCreateRequest request);

	/** REVIEW_MANAGE write: PUBLISHED, HIDDEN or UNDER_REVIEW. */
	NexHomeReviewResponse updateReviewStatus(NexHomeReviewStatusUpdateRequest request);

	/** The booking's review: its customer and partner, or BOOKING_MANAGE / REVIEW_MANAGE read; 404 without one. */
	NexHomeReviewResponse getReviewOfBooking(Long bookingId);

	/** REVIEW_MANAGE read: paged, newest first, only the filters given. */
	NexHomePageResponse<NexHomeReviewResponse> getReviews(Integer page, Integer size, String reviewStatus,
			Long partnerProfileId, Long customerProfileId, Integer rating, String search);

	/** The caller's reviews: about them as a partner (PUBLISHED only), else written by them as a customer. */
	NexHomePageResponse<NexHomeReviewResponse> getMyReviews(Integer page, Integer size);

	/** REVIEW_MANAGE read. */
	NexHomeReviewStatsResponse getStats();

	/**
	 * One partner's (published) or one customer's (written) reviews at a glance: exactly one of the ids; the partner
	 * or customer themself, or REVIEW_MANAGE read.
	 */
	com.nexhome.app.dto.NexHomeReviewSummaryResponse getSummary(Long partnerProfileId, Long customerProfileId);
}
