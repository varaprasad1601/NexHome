package com.nexhome.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeReviewCreateRequest;
import com.nexhome.app.dto.NexHomeReviewResponse;
import com.nexhome.app.dto.NexHomeReviewStatsResponse;
import com.nexhome.app.dto.NexHomeReviewStatusUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeReviewMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeBooking;
import com.nexhome.app.model.NexHomeBookingReview;
import com.nexhome.app.model.NexHomeLookupCodes.BookingStatus;
import com.nexhome.app.model.NexHomeLookupCodes.NotificationType;
import com.nexhome.app.model.NexHomeLookupCodes.ReviewStatus;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.repository.NexHomeBookingRepository;
import com.nexhome.app.repository.NexHomeBookingReviewRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NexHomeReviewServiceImpl implements NexHomeReviewService {

	static final String REVIEW_MANAGE = "REVIEW_MANAGE";
	private static final String BOOKING_MANAGE = "BOOKING_MANAGE";
	private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
	/** Months in the dashboard's rating trend, this one included. */
	private static final int STATS_MONTHS = 10;
	/** A partner rating this low tells the staff who read reviews. */
	private static final int LOW_RATING = 2;

	private final NexHomeBookingReviewRepository reviewRepository;
	private final NexHomeBookingRepository bookingRepository;
	private final NexHomeCustomerProfileRepository customerProfileRepository;
	private final NexHomePartnerProfileRepository partnerProfileRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeReviewMapper reviewMapper;
	private final NexHomeNotifier notifier;
	private final NexHomeAuditRecorder auditRecorder;

	@Override
	@Transactional
	public NexHomeReviewResponse createReview(NexHomeReviewCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeBooking booking = bookingRepository.findById(request.bookingId())
				.orElseThrow(() -> new ResourceNotFoundException("Booking does not exist."));
		boolean ownBooking = booking.getCustomer().getUser().getId().equals(actingUserId);
		if (!ownBooking && !permissionService.can(REVIEW_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Only the booking's customer can review it.");
		}
		if (!BookingStatus.COMPLETED.equals(booking.getBookingStatus()) || booking.getPartner() == null) {
			throw new InvalidRequestException("Only a completed booking can be reviewed.");
		}
		if (reviewRepository.existsByBookingId(booking.getId())) {
			throw new DuplicateResourceException("This booking already has a review.");
		}

		NexHomeBookingReview review = new NexHomeBookingReview();
		review.setBooking(booking);
		review.setCustomer(booking.getCustomer());
		review.setPartner(booking.getPartner());
		review.setServiceRating(request.serviceRating().shortValue());
		review.setPartnerRating(request.partnerRating().shortValue());
		review.setReviewText(NexHomeText.trimToNull(request.reviewText()));
		review.setReviewStatus(ReviewStatus.PUBLISHED);
		review.setCreatedBy(actingUserId);
		review.setUpdatedBy(actingUserId);
		reviewRepository.save(review);

		NexHomeNotifier.Message received = new NexHomeNotifier.Message(NotificationType.REVIEW,
				"New review: " + request.partnerRating() + " star" + (request.partnerRating() == 1 ? "" : "s"),
				"The customer rated your work on booking " + booking.getPublicId() + " "
						+ request.partnerRating() + " out of 5.",
				NexHomeNotifier.REVIEW, review.getId());
		notifier.notify(booking.getPartner().getUser().getId(), received, actingUserId);
		if (request.partnerRating() <= LOW_RATING) {
			notifier.notifyStaff(REVIEW_MANAGE, new NexHomeNotifier.Message(NotificationType.REVIEW,
					"Low rating on " + booking.getPublicId(),
					"Partner " + booking.getPartner().getPublicId() + " was rated " + request.partnerRating()
							+ " out of 5.",
					NexHomeNotifier.REVIEW, review.getId()), actingUserId);
		}
		log.info("Review added to booking {} by User ID {}", booking.getPublicId(), actingUserId);
		return reviewMapper.toReviewDto(review);
	}

	@Override
	@Transactional
	public NexHomeReviewResponse updateReviewStatus(NexHomeReviewStatusUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		String status = lookupService.validateCode(Types.REVIEW_STATUS, request.reviewStatus(), "Review status");
		NexHomeBookingReview review = reviewRepository.findById(request.reviewId())
				.orElseThrow(() -> new ResourceNotFoundException("Review does not exist."));
		String previous = review.getReviewStatus();
		if (previous.equals(status)) {
			throw new InvalidRequestException("The review is already " + status + ".");
		}
		review.setReviewStatus(status);
		review.setUpdatedBy(actingUserId);
		auditRecorder.record("STATUS_CHANGED", "REVIEW", review.getId(), Map.of("reviewStatus", previous),
				Map.of("reviewStatus", status), actingUserId);
		log.info("Review {} moved from {} to {} by User ID {}", review.getId(), previous, status, actingUserId);
		return reviewMapper.toReviewDto(review);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeReviewResponse getReviewOfBooking(Long bookingId) {
		NexHomeBooking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking does not exist."));
		Long userId = permissionService.currentUserId();
		boolean involved = booking.getCustomer().getUser().getId().equals(userId)
				|| booking.getPartner() != null && booking.getPartner().getUser().getId().equals(userId);
		if (!involved && !permissionService.can(BOOKING_MANAGE, NexHomeAccess.READ.name())
				&& !permissionService.can(REVIEW_MANAGE, NexHomeAccess.READ.name())) {
			throw new AccessDeniedException("You do not have permission to see this booking's review.");
		}
		return reviewRepository.findByBookingId(bookingId)
				.map(reviewMapper::toReviewDto)
				.orElseThrow(() -> new ResourceNotFoundException("This booking has no review."));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeReviewResponse> getReviews(Integer page, Integer size, String reviewStatus,
			Long partnerProfileId, Long customerProfileId, Integer rating, String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		String status = NexHomeText.trimToNull(reviewStatus) == null ? null
				: lookupService.validateCode(Types.REVIEW_STATUS, reviewStatus, "Review status");
		if (rating != null && (rating < 1 || rating > 5)) {
			throw new InvalidRequestException("Rating must be between 1 and 5.");
		}
		Page<NexHomeBookingReview> reviews = reviewRepository.findAll(NexHomeBookingReviewRepository.reviewSpec(status,
				partnerProfileId, customerProfileId, rating == null ? null : rating.shortValue(),
				NexHomePaging.searchText(search)), pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, reviews,
				reviews.stream().map(reviewMapper::toReviewDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeReviewResponse> getMyReviews(Integer page, Integer size) {
		Long userId = permissionService.currentUserId();
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomeBookingReview> reviews = partnerProfileRepository.findByUserId(userId)
				.map(partner -> reviewRepository.findAll(NexHomeBookingReviewRepository.reviewSpec(
						ReviewStatus.PUBLISHED, partner.getId(), null, null, null), pageRequest))
				.or(() -> customerProfileRepository.findByUserId(userId)
						.map(customer -> reviewRepository.findAll(NexHomeBookingReviewRepository.reviewSpec(null,
								null, customer.getId(), null, null), pageRequest)))
				.orElseThrow(() -> new ResourceNotFoundException("You do not have a customer or partner profile."));
		return NexHomePaging.toPageResponse(pageRequest, reviews,
				reviews.stream().map(reviewMapper::toReviewDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeReviewStatsResponse getStats() {
		long total = 0;
		Map<String, Long> byStatus = new LinkedHashMap<>();
		BigDecimal partnerAverage = null;
		BigDecimal serviceAverage = null;
		for (Object[] row : reviewRepository.countByStatus()) {
			long count = ((Number) row[1]).longValue();
			byStatus.put((String) row[0], count);
			total += count;
			if (ReviewStatus.PUBLISHED.equals(row[0])) {
				partnerAverage = oneDecimal((Number) row[2]);
				serviceAverage = oneDecimal((Number) row[3]);
			}
		}
		Map<Integer, Long> ratingCounts = new LinkedHashMap<>();
		for (int rating = 1; rating <= 5; rating++) {
			ratingCounts.put(rating, 0L);
		}
		reviewRepository.countPublishedByPartnerRating().forEach(row -> ratingCounts.put(((Number) row[0]).intValue(),
				((Number) row[1]).longValue()));

		// The running average at the end of each of the last months
		YearMonth current = YearMonth.now(ZONE);
		YearMonth first = current.minusMonths(STATS_MONTHS - 1);
		Map<YearMonth, NexHomeBookingReviewRepository.MonthlyRatings> monthly = new LinkedHashMap<>();
		reviewRepository.publishedByMonth().forEach(row -> monthly.put(YearMonth.parse(row.getMonth()), row));
		long count = 0;
		long sum = 0;
		for (Map.Entry<YearMonth, NexHomeBookingReviewRepository.MonthlyRatings> entry : monthly.entrySet()) {
			if (entry.getKey().isBefore(first)) {
				count += entry.getValue().getTotal();
				sum += entry.getValue().getRatingSum();
			}
		}
		List<NexHomeReviewStatsResponse.MonthRating> months = new ArrayList<>();
		for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
			Optional<NexHomeBookingReviewRepository.MonthlyRatings> row = Optional.ofNullable(monthly.get(month));
			long inMonth = row.map(NexHomeBookingReviewRepository.MonthlyRatings::getTotal).orElse(0L);
			count += inMonth;
			sum += row.map(NexHomeBookingReviewRepository.MonthlyRatings::getRatingSum).orElse(0L);
			months.add(new NexHomeReviewStatsResponse.MonthRating(month.toString(), inMonth,
					count == 0 ? null : oneDecimal((double) sum / count)));
		}
		long publishedThisMonth = Optional.ofNullable(monthly.get(current))
				.map(NexHomeBookingReviewRepository.MonthlyRatings::getTotal).orElse(0L);
		return new NexHomeReviewStatsResponse(total, byStatus.getOrDefault(ReviewStatus.PUBLISHED, 0L),
				byStatus.getOrDefault(ReviewStatus.HIDDEN, 0L), byStatus.getOrDefault(ReviewStatus.UNDER_REVIEW, 0L),
				partnerAverage, serviceAverage, ratingCounts, publishedThisMonth, months);
	}

	@Override
	@Transactional(readOnly = true)
	public com.nexhome.app.dto.NexHomeReviewSummaryResponse getSummary(Long partnerProfileId, Long customerProfileId) {
		if ((partnerProfileId == null) == (customerProfileId == null)) {
			throw new InvalidRequestException("Give either a partner profile ID or a customer profile ID.");
		}
		Long ownerUserId = partnerProfileId != null
				? partnerProfileRepository.findById(partnerProfileId)
						.orElseThrow(() -> new ResourceNotFoundException("Partner profile does not exist."))
						.getUser().getId()
				: customerProfileRepository.findById(customerProfileId)
						.orElseThrow(() -> new ResourceNotFoundException("Customer profile does not exist."))
						.getUser().getId();
		if (!ownerUserId.equals(permissionService.currentUserId())
				&& !permissionService.can(REVIEW_MANAGE, NexHomeAccess.READ.name())) {
			throw new AccessDeniedException("You do not have permission to see these reviews.");
		}
		Map<Integer, Long> counts = new LinkedHashMap<>();
		for (int rating = 5; rating >= 1; rating--) {
			counts.put(rating, 0L);
		}
		(partnerProfileId != null ? reviewRepository.countPublishedOfPartnerByRating(partnerProfileId)
				: reviewRepository.countGivenByCustomerByRating(customerProfileId))
				.forEach(row -> counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue()));
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		long sum = counts.entrySet().stream().mapToLong(entry -> entry.getKey() * entry.getValue()).sum();
		return new com.nexhome.app.dto.NexHomeReviewSummaryResponse(total == 0 ? null : oneDecimal((double) sum / total),
				total, counts);
	}

	/** An average to one decimal place (HALF_UP); null stays null. */
	static BigDecimal oneDecimal(Number value) {
		return value == null ? null : BigDecimal.valueOf(value.doubleValue()).setScale(1, RoundingMode.HALF_UP);
	}
}
