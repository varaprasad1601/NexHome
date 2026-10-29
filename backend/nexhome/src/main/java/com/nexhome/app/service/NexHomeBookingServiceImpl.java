package com.nexhome.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeAssignmentCancelRequest;
import com.nexhome.app.dto.NexHomeAssignmentOfferRequest;
import com.nexhome.app.dto.NexHomeAssignmentRespondRequest;
import com.nexhome.app.dto.NexHomeAuditEntry;
import com.nexhome.app.dto.NexHomeBookingAssignmentResponse;
import com.nexhome.app.dto.NexHomeBookingCancelRequest;
import com.nexhome.app.dto.NexHomeBookingCreateRequest;
import com.nexhome.app.dto.NexHomeBookingDetailResponse;
import com.nexhome.app.dto.NexHomeBookingHistoryResponse;
import com.nexhome.app.dto.NexHomeBookingResponse;
import com.nexhome.app.dto.NexHomeBookingStatsResponse;
import com.nexhome.app.dto.NexHomeBookingStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeBookingTimerRequest;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeCustomerBookingStatsResponse;
import com.nexhome.app.dto.NexHomePartnerJobStatsResponse;
import com.nexhome.app.dto.NexHomePartnerLocationResponse;
import com.nexhome.app.dto.NexHomePartnerLocationUpdateRequest;
import com.nexhome.app.dto.NexHomeServiceQuoteResponse;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeBookingMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeBooking;
import com.nexhome.app.model.NexHomeBookingAssignment;
import com.nexhome.app.model.NexHomeBookingItem;
import com.nexhome.app.model.NexHomeBookingStatusHistory;
import com.nexhome.app.model.NexHomeBookingTimeLog;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeLookupCodes.AssignmentStatus;
import com.nexhome.app.model.NexHomeLookupCodes.PartnerOnboardingStatus;
import com.nexhome.app.model.NexHomeLookupCodes.BookingStatus;
import com.nexhome.app.model.NexHomeLookupCodes.BookingType;
import com.nexhome.app.model.NexHomeLookupCodes.BookingTimerEvent;
import com.nexhome.app.model.NexHomeLookupCodes.NotificationType;
import com.nexhome.app.model.NexHomeLookupCodes.PricingType;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomePartnerLocationUpdate;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomeServiceCategory;
import com.nexhome.app.repository.NexHomeBookingAssignmentRepository;
import com.nexhome.app.repository.NexHomeBookingItemRepository;
import com.nexhome.app.repository.NexHomeBookingRepository;
import com.nexhome.app.repository.NexHomeBookingReviewRepository;
import com.nexhome.app.repository.NexHomeBookingStatusHistoryRepository;
import com.nexhome.app.repository.NexHomeBookingTimeLogRepository;
import com.nexhome.app.repository.NexHomeCashCollectionRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerLocationUpdateRepository;
import com.nexhome.app.repository.NexHomePartnerPayoutRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerServiceRepository;
import com.nexhome.app.repository.NexHomePaymentRefundRepository;
import com.nexhome.app.repository.NexHomePaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeBookingServiceImpl implements NexHomeBookingService {

	static final String BOOKING_MANAGE = "BOOKING_MANAGE";
	private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
	/** Months in the dashboard's booking trend, this one included. */
	private static final int STATS_MONTHS = 10;
	private static final int DEFAULT_OFFER_MINUTES = 30;
	/** Assignments that hold the booking before the work starts: assigned by staff, or accepted by the partner. */
	private static final Set<String> HOLDING = Set.of(AssignmentStatus.ASSIGNED, AssignmentStatus.ACCEPTED);
	/** The history reason when staff take the partner off (the console shows it as "Unassigned"). */
	static final String PARTNER_UNASSIGNED = "Partner unassigned";

	/** Status changes staff make with the status request (the rest come from offers, the timer and cancelling). */
	private static final Map<String, Set<String>> STAFF_TRANSITIONS = Map.of(
			BookingStatus.PENDING, Set.of(BookingStatus.CONFIRMED),
			BookingStatus.ASSIGNED, Set.of(BookingStatus.PARTNER_EN_ROUTE));
	/** The only status change the assigned partner makes with the status request. */
	private static final Map<String, Set<String>> PARTNER_TRANSITIONS = Map.of(
			BookingStatus.ASSIGNED, Set.of(BookingStatus.PARTNER_EN_ROUTE));
	/** A booking can be cancelled until the work starts. */
	private static final Set<String> CANCELLABLE = Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED,
			BookingStatus.ASSIGNED, BookingStatus.PARTNER_EN_ROUTE);
	/** While the partner is on the way or working, their location is shared. */
	private static final Set<String> TRACKED = Set.of(BookingStatus.ASSIGNED, BookingStatus.PARTNER_EN_ROUTE,
			BookingStatus.IN_PROGRESS, BookingStatus.PAUSED);
	/** Timer event: the statuses it may follow, and the status it leads to. */
	private static final Map<String, Set<String>> TIMER_FROM = Map.of(
			BookingTimerEvent.START, Set.of(BookingStatus.ASSIGNED, BookingStatus.PARTNER_EN_ROUTE),
			BookingTimerEvent.PAUSE, Set.of(BookingStatus.IN_PROGRESS),
			BookingTimerEvent.RESUME, Set.of(BookingStatus.PAUSED),
			BookingTimerEvent.STOP, Set.of(BookingStatus.IN_PROGRESS, BookingStatus.PAUSED));
	private static final Map<String, String> TIMER_TO = Map.of(
			BookingTimerEvent.START, BookingStatus.IN_PROGRESS,
			BookingTimerEvent.PAUSE, BookingStatus.PAUSED,
			BookingTimerEvent.RESUME, BookingStatus.IN_PROGRESS,
			BookingTimerEvent.STOP, BookingStatus.COMPLETED);

	private final NexHomeBookingRepository bookingRepository;
	private final NexHomeBookingItemRepository bookingItemRepository;
	private final NexHomeBookingStatusHistoryRepository historyRepository;
	private final NexHomeBookingAssignmentRepository assignmentRepository;
	private final NexHomeBookingTimeLogRepository timeLogRepository;
	private final NexHomePartnerLocationUpdateRepository locationRepository;
	private final NexHomeCustomerProfileRepository customerProfileRepository;
	private final NexHomePartnerProfileRepository partnerProfileRepository;
	private final NexHomePartnerServiceRepository partnerServiceRepository;
	private final NexHomeBookingCreator bookingCreator;
	private final NexHomeAvailabilityService availabilityService;
	private final NexHomeCatalogService catalogService;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeAuditUsers auditUsers;
	private final NexHomeBookingMapper bookingMapper;
	private final NexHomeSettlement settlement;
	private final NexHomePartnerPayoutRepository payoutRepository;
	private final NexHomeCashCollectionRepository cashCollectionRepository;
	private final NexHomePaymentRepository paymentRepository;
	private final NexHomePaymentRefundRepository refundRepository;
	private final NexHomeBookingReviewRepository reviewRepository;
	private final NexHomeNotifier notifier;
	private final NexHomeAuditRecorder auditRecorder;

	// ------------------------------------------------------------------------------------------------------------
	// Creating and reading
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomeBookingResponse createBooking(NexHomeBookingCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeCustomerProfile customer = customerProfileRepository.findById(request.customerProfileId())
				.orElseThrow(() -> new ResourceNotFoundException("Customer profile does not exist."));
		NexHomeBooking booking = bookingCreator.create(new NexHomeBookingCreator.Order(customer, request.serviceId(),
				request.serviceVariantId(), request.addressId(), request.bookingType(), request.scheduledStartAt(),
				request.paymentMethod(), request.minutes(), null, Boolean.TRUE.equals(request.slotRequested())),
				actingUserId);
		tell(customer.getUser().getId(), "Booking " + booking.getPublicId() + " created",
				"We booked " + booking.getServiceNameSnapshot() + " for you.", booking, actingUserId);
		log.info("Booking {} created for customer {} by User ID {}", booking.getPublicId(), customer.getPublicId(),
				actingUserId);
		return bookingMapper.toBookingDto(booking);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeBookingResponse> getBookings(Integer page, Integer size, String bookingStatus,
			String paymentStatus, Long customerProfileId, Long partnerProfileId, Long serviceId, Instant from,
			Instant to, String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomeBooking> bookings = bookingRepository.findAll(NexHomeBookingRepository.bookingSpec(
				optionalCode(Types.BOOKING_STATUS, bookingStatus, "Booking status"),
				optionalCode(Types.BOOKING_PAYMENT_STATUS, paymentStatus, "Payment status"),
				customerProfileId, partnerProfileId, serviceId, from, to, NexHomePaging.searchText(search)),
				pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, bookings,
				bookings.stream().map(bookingMapper::toBookingDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePartnerJobStatsResponse getPartnerJobStats(Long partnerProfileId) {
		NexHomePartnerProfile partner = partnerProfileRepository.findById(partnerProfileId)
				.orElseThrow(() -> new ResourceNotFoundException("Partner profile does not exist."));
		boolean own = partner.getUser().getId().equals(permissionService.currentUserId());
		if (!own && !permissionService.can(BOOKING_MANAGE, NexHomeAccess.READ.name())) {
			throw new AccessDeniedException("You do not have permission to see this partner's jobs.");
		}
		JobStages stages = JobStages.of(bookingRepository.countStagesOfPartner(partnerProfileId));
		// Earnings: the partner's share only, as on the Earnings tab: the net of the payouts for online jobs, plus
		// the cash collected on cash jobs less the service's platform commission (the platform's share of it)
		BigDecimal earnings = own || permissionService.can("PAYOUT_MANAGE", NexHomeAccess.READ.name())
				? payoutRepository.sumNetOfPartner(partnerProfileId)
						.add(cashCollectionRepository.findCollectedByPartnerWithService(partnerProfileId).stream()
								.map(cash -> cash.getAmount().subtract(
										NexHomeBookingMapper.commissionOf(cash.getBooking().getService(), cash.getAmount())))
								.reduce(BigDecimal.ZERO, BigDecimal::add))
				: null;
		return new NexHomePartnerJobStatsResponse(stages.total(), stages.assigned(), stages.accepted(),
				stages.inProgress(), stages.completed(), stages.cancelled(),
				assignmentRepository.countOfPartnerByStatus(partnerProfileId, AssignmentStatus.REJECTED), earnings,
				averageOf(reviewRepository.partnerRatingOf(partnerProfileId)));
	}

	/** The average of an [average, count] row to one decimal place; null without reviews. */
	private static BigDecimal averageOf(List<Object[]> row) {
		return row.isEmpty() ? null : NexHomeReviewServiceImpl.oneDecimal((Number) row.get(0)[0]);
	}

	/**
	 * Bookings by stage, from [booking status, holding assignment status, count] rows, for the partner and customer
	 * statistics alike: pending, confirmed, assigned (the partner has not accepted yet), accepted (accepted, or the
	 * partner is on the way), in progress (working or paused), completed and cancelled.
	 */
	private record JobStages(long total, long pending, long confirmed, long assigned, long accepted, long inProgress,
			long completed, long cancelled) {

		static JobStages of(List<Object[]> rows) {
			long total = 0;
			long pending = 0;
			long confirmed = 0;
			long assigned = 0;
			long accepted = 0;
			long inProgress = 0;
			long completed = 0;
			long cancelled = 0;
			for (Object[] row : rows) {
				long count = ((Number) row[2]).longValue();
				total += count;
				switch ((String) row[0]) {
					case BookingStatus.PENDING -> pending += count;
					case BookingStatus.CONFIRMED -> confirmed += count;
					case BookingStatus.ASSIGNED -> {
						if (AssignmentStatus.ACCEPTED.equals(row[1])) {
							accepted += count;
						} else {
							assigned += count;
						}
					}
					// On the way follows accepting
					case BookingStatus.PARTNER_EN_ROUTE -> accepted += count;
					case BookingStatus.IN_PROGRESS, BookingStatus.PAUSED -> inProgress += count;
					case BookingStatus.COMPLETED -> completed += count;
					case BookingStatus.CANCELLED -> cancelled += count;
					default -> {
					}
				}
			}
			return new JobStages(total, pending, confirmed, assigned, accepted, inProgress, completed, cancelled);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeCustomerBookingStatsResponse getCustomerBookingStats(Long customerProfileId) {
		NexHomeCustomerProfile customer = customerProfileRepository.findById(customerProfileId)
				.orElseThrow(() -> new ResourceNotFoundException("Customer profile does not exist."));
		if (!customer.getUser().getId().equals(permissionService.currentUserId())
				&& !permissionService.can(BOOKING_MANAGE, NexHomeAccess.READ.name())) {
			throw new AccessDeniedException("You do not have permission to see this customer's bookings.");
		}
		JobStages stages = JobStages.of(bookingRepository.countStagesOfCustomer(customerProfileId));
		// Spend: online payments (the transaction fee included) and cash, less what was refunded
		BigDecimal spend = paymentRepository.sumCapturedOfCustomer(customerProfileId)
				.add(cashCollectionRepository.sumCollectedFromCustomer(customerProfileId))
				.subtract(refundRepository.sumSucceededOfCustomer(customerProfileId));
		return new NexHomeCustomerBookingStatsResponse(stages.total(), stages.pending(), stages.confirmed(),
				stages.assigned(), stages.accepted(), stages.inProgress(), stages.completed(), stages.cancelled(),
				assignmentRepository.countOfCustomerByStatus(customerProfileId, AssignmentStatus.REJECTED), spend,
				averageOf(reviewRepository.ratingsGivenBy(customerProfileId)));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeBookingStatsResponse getBookingStats() {
		Map<String, Long> byStatus = new LinkedHashMap<>();
		long total = 0;
		for (Object[] row : bookingRepository.countByStatus()) {
			long count = ((Number) row[1]).longValue();
			byStatus.put((String) row[0], count);
			total += count;
		}
		LocalDate today = LocalDate.now(ZONE);
		long scheduledToday = bookingRepository.countScheduledBetween(today.atStartOfDay(ZONE).toInstant(),
				today.plusDays(1).atStartOfDay(ZONE).toInstant());
		YearMonth current = YearMonth.now(ZONE);
		Map<YearMonth, Long> made = new LinkedHashMap<>();
		for (NexHomeBookingRepository.MonthlyCount row : bookingRepository.countByMonth()) {
			made.put(YearMonth.parse(row.getMonth()), row.getTotal());
		}
		YearMonth first = current.minusMonths(STATS_MONTHS - 1);
		// Months before the window count towards its start
		long running = made.entrySet().stream()
				.filter(entry -> entry.getKey().isBefore(first))
				.mapToLong(Map.Entry::getValue)
				.sum();
		List<NexHomeBookingStatsResponse.MonthStats> months = new ArrayList<>();
		for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
			long inMonth = made.getOrDefault(month, 0L);
			running += inMonth;
			months.add(new NexHomeBookingStatsResponse.MonthStats(month.toString(), inMonth, running));
		}
		return new NexHomeBookingStatsResponse(total, byStatus, scheduledToday, made.getOrDefault(current, 0L),
				months);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeBookingResponse> getMyCustomerBookings(Integer page, Integer size) {
		NexHomeCustomerProfile customer = customerProfileRepository.findByUserId(permissionService.currentUserId())
				.orElseThrow(() -> new ResourceNotFoundException("You do not have a customer profile."));
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomeBooking> bookings = bookingRepository.findAll(NexHomeBookingRepository.bookingSpec(null, null,
				customer.getId(), null, null, null, null, null), pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, bookings,
				bookings.stream().map(bookingMapper::toBookingDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeBookingResponse> getMyPartnerBookings(Integer page, Integer size) {
		NexHomePartnerProfile partner = myPartnerProfile()
				.orElseThrow(() -> new ResourceNotFoundException("You do not have a partner profile."));
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomeBooking> bookings = bookingRepository.findAll(NexHomeBookingRepository.bookingSpec(null, null,
				null, partner.getId(), null, null, null, null), pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, bookings,
				bookings.stream().map(bookingMapper::toBookingDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeBookingAssignmentResponse> getMyOffers(Integer page, Integer size) {
		NexHomePartnerProfile partner = myPartnerProfile()
				.orElseThrow(() -> new ResourceNotFoundException("You do not have a partner profile."));
		PageRequest pageRequest = NexHomePaging.of(page, size, org.springframework.data.domain.Sort.unsorted());
		Page<NexHomeBookingAssignment> offers = assignmentRepository
				.findByPartnerIdAndAssignmentStatusOrderByOfferedAtDesc(partner.getId(), AssignmentStatus.OFFERED,
						pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, offers,
				offers.stream().map(bookingMapper::toAssignmentDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeBookingDetailResponse getBooking(Long bookingId) {
		NexHomeBooking booking = findBooking(bookingId);
		requireCanSee(booking);
		return detailOf(booking);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Status and cancelling
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomeBookingResponse updateBookingStatus(NexHomeBookingStatusUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeBooking booking = findBooking(request.bookingId());
		String target = lookupService.validateCode(Types.BOOKING_STATUS, request.bookingStatus(), "Booking status");
		String current = booking.getBookingStatus();
		boolean staff = permissionService.can(BOOKING_MANAGE, NexHomeAccess.WRITE.name());
		Map<String, Set<String>> allowed;
		if (staff) {
			allowed = STAFF_TRANSITIONS;
		} else if (isAssignedPartner(booking)) {
			allowed = PARTNER_TRANSITIONS;
		} else {
			throw new AccessDeniedException("Only staff and the booking's partner can change its status.");
		}
		if (!allowed.getOrDefault(current, Set.of()).contains(target)) {
			throw new InvalidRequestException("A booking that is " + current + " cannot be moved to " + target
					+ " here.");
		}

		changeStatus(booking, target, NexHomeText.trimToNull(request.reason()), actingUserId);
		// PARTNER_EN_ROUTE is "Partner Accepted": an assignment not answered yet is accepted with it
		if (BookingStatus.PARTNER_EN_ROUTE.equals(target)) {
			assignmentRepository.findByBookingIdAndAssignmentStatus(booking.getId(), AssignmentStatus.ASSIGNED)
					.forEach(assignment -> {
						assignment.setAssignmentStatus(AssignmentStatus.ACCEPTED);
						assignment.setRespondedAt(Instant.now());
						assignment.setUpdatedBy(actingUserId);
					});
		}
		if (BookingStatus.CONFIRMED.equals(target)) {
			tell(customerUserOf(booking), "Booking " + booking.getPublicId() + " confirmed",
					booking.getServiceNameSnapshot() + " is confirmed; we will assign a partner.", booking,
					actingUserId);
		}
		else if (BookingStatus.PARTNER_EN_ROUTE.equals(target)) {
			tell(customerUserOf(booking), "Partner accepted " + booking.getPublicId(),
					"Your partner accepted the job for " + booking.getServiceNameSnapshot() + ".", booking,
					actingUserId);
		}
		if (staff) {
			auditRecorder.record("STATUS_CHANGED", "BOOKING", booking.getPublicId(),
					Map.of("bookingStatus", current), Map.of("bookingStatus", target), actingUserId);
		}
		log.info("Booking {} moved from {} to {} by User ID {}", booking.getPublicId(), current, target,
				actingUserId);
		return bookingMapper.toBookingDto(booking);
	}

	@Override
	@Transactional
	public NexHomeBookingResponse cancelBooking(NexHomeBookingCancelRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeBooking booking = findBooking(request.bookingId());
		if (!isCustomer(booking) && !permissionService.can(BOOKING_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Only the customer and staff can cancel a booking.");
		}
		if (!CANCELLABLE.contains(booking.getBookingStatus())) {
			throw new InvalidRequestException("A booking that is " + booking.getBookingStatus()
					+ " cannot be cancelled.");
		}
		String reason = NexHomeText.trimToNull(request.reason());

		// Every offer and the accepted assignment end, and the partner's time is freed
		assignmentRepository.findByBookingIdOrderByOfferedAtDescIdDesc(booking.getId()).stream()
				.filter(assignment -> AssignmentStatus.OFFERED.equals(assignment.getAssignmentStatus())
						|| HOLDING.contains(assignment.getAssignmentStatus()))
				.forEach(assignment -> {
					assignment.setAssignmentStatus(AssignmentStatus.CANCELLED);
					assignment.setUpdatedBy(actingUserId);
				});
		// A paid booking is refunded in full (cancellation fees come with Module 8)
		settlement.refundOnCancel(booking, actingUserId);
		String previous = booking.getBookingStatus();
		booking.setCancellationReason(reason);
		// A cancelled booking has no payment status; its payments and refunds keep the history (V42)
		booking.setPaymentStatus(null);
		changeStatus(booking, BookingStatus.CANCELLED, reason, actingUserId);

		String cancelled = "Booking " + booking.getPublicId() + " cancelled";
		tell(customerUserOf(booking), cancelled, reason == null ? booking.getServiceNameSnapshot() : reason, booking,
				actingUserId);
		tell(partnerUserOf(booking), cancelled, reason == null ? booking.getServiceNameSnapshot() : reason, booking,
				actingUserId);
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("bookingStatus", BookingStatus.CANCELLED);
		after.put("reason", reason);
		auditRecorder.record("CANCELLED", "BOOKING", booking.getPublicId(), Map.of("bookingStatus", previous), after,
				actingUserId);
		log.info("Booking {} cancelled by User ID {}", booking.getPublicId(), actingUserId);
		return bookingMapper.toBookingDto(booking);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Assignment
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomeBookingAssignmentResponse offerBooking(NexHomeAssignmentOfferRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeBooking booking = findBooking(request.bookingId());
		if (!BookingStatus.CONFIRMED.equals(booking.getBookingStatus())) {
			throw new InvalidRequestException("Only a CONFIRMED booking without a partner can be offered; this one is "
					+ booking.getBookingStatus() + ".");
		}
		NexHomePartnerProfile partner = partnerProfileRepository.findById(request.partnerProfileId())
				.orElseThrow(() -> new ResourceNotFoundException("Partner profile does not exist."));
		if (assignmentRepository.existsByBookingIdAndPartnerIdAndAssignmentStatus(booking.getId(), partner.getId(),
				AssignmentStatus.OFFERED)) {
			throw new DuplicateResourceException("The booking is already offered to this partner.");
		}
		int minutes = request.expiresInMinutes() != null ? request.expiresInMinutes() : DEFAULT_OFFER_MINUTES;
		if (request.startsAt() != null || request.endsAt() != null) {
			allotSlot(booking, request.startsAt(), request.endsAt(), actingUserId);
		}
		if (booking.getScheduledStartAt() != null) {
			// The partner must be able to take it then: approved, offering the service, working then, not on time off
			// and without another booking (no slot is held: holds are not used for now)
			availabilityService.requireFree(partner.getId(), booking.getService().getId(),
					booking.getScheduledStartAt(), endOf(booking), Boolean.TRUE.equals(request.outsideWeeklyHours()),
					booking.getId());
		} else {
			// An INSTANT booking has no time to hold: the partner must only be able to take the service
			requireCanTake(partner, booking.getService().getId());
		}

		NexHomeBookingAssignment assignment = new NexHomeBookingAssignment();
		assignment.setBooking(booking);
		assignment.setPartner(partner);
		assignment.setAssignedBy(actingUserId);
		assignment.setAssignmentStatus(AssignmentStatus.OFFERED);
		assignment.setOfferedAt(Instant.now());
		assignment.setExpiresAt(Instant.now().plus(Duration.ofMinutes(minutes)));
		assignment.setCreatedBy(actingUserId);
		assignment.setUpdatedBy(actingUserId);
		assignment = assignmentRepository.save(assignment);
		log.info("Booking {} offered to partner {} by User ID {}", booking.getPublicId(), partner.getPublicId(),
				actingUserId);

		boolean direct = Boolean.TRUE.equals(request.direct());
		if (direct) {
			take(assignment, AssignmentStatus.ASSIGNED, actingUserId);
		}
		tell(partner.getUser().getId(), (direct ? "New job assigned: " : "New job offer: ") + booking.getPublicId(),
				booking.getServiceNameSnapshot() + (direct ? "; accept or decline it." : "; the offer runs for "
						+ minutes + " minutes."),
				booking, actingUserId);
		auditRecorder.record(direct ? "PARTNER_ASSIGNED" : "OFFERED", "BOOKING", booking.getPublicId(), null,
				Map.of("partner", partner.getPublicId()), actingUserId);
		return bookingMapper.toAssignmentDto(assignment);
	}

	@Override
	@Transactional
	public NexHomeBookingAssignmentResponse respondToOffer(NexHomeAssignmentRespondRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeBookingAssignment assignment = findAssignment(request.assignmentId());
		boolean ownOffer = assignment.getPartner().getUser().getId().equals(actingUserId);
		if (!ownOffer && !permissionService.can(BOOKING_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Only the partner it is offered to, or staff, can answer an offer.");
		}
		if (AssignmentStatus.ASSIGNED.equals(assignment.getAssignmentStatus())) {
			return answerAssignment(assignment, request, actingUserId);
		}
		if (!AssignmentStatus.OFFERED.equals(assignment.getAssignmentStatus())) {
			throw new InvalidRequestException("The offer is " + assignment.getAssignmentStatus()
					+ " and can no longer be answered.");
		}
		if (assignment.getExpiresAt() != null && !assignment.getExpiresAt().isAfter(Instant.now())) {
			throw new InvalidRequestException("The offer has expired.");
		}

		if (Boolean.TRUE.equals(request.accept())) {
			if (!BookingStatus.CONFIRMED.equals(assignment.getBooking().getBookingStatus())) {
				throw new InvalidRequestException("The booking is " + assignment.getBooking().getBookingStatus()
						+ " and cannot be accepted.");
			}
			take(assignment, AssignmentStatus.ACCEPTED, actingUserId);
			partnerAccepted(assignment, actingUserId);
		} else {
			String reason = NexHomeText.trimToNull(request.rejectionReason());
			if (reason == null) {
				throw new InvalidRequestException("Rejection reason is required to reject an offer.");
			}
			assignment.setAssignmentStatus(AssignmentStatus.REJECTED);
			assignment.setRejectionReason(reason);
			assignment.setRespondedAt(Instant.now());
			assignment.setUpdatedBy(actingUserId);
			partnerDeclined(assignment, reason, actingUserId);
			log.info("Partner {} rejected booking {}", assignment.getPartner().getPublicId(),
					assignment.getBooking().getPublicId());
		}
		return bookingMapper.toAssignmentDto(assignment);
	}

	@Override
	@Transactional
	public NexHomeBookingAssignmentResponse cancelAssignment(NexHomeAssignmentCancelRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeBookingAssignment assignment = findAssignment(request.assignmentId());
		NexHomeBooking booking = assignment.getBooking();
		String status = assignment.getAssignmentStatus();
		boolean holding = HOLDING.contains(status);
		if (!AssignmentStatus.OFFERED.equals(status) && !holding) {
			throw new InvalidRequestException("Only an OFFERED, ASSIGNED or ACCEPTED assignment can be cancelled.");
		}
		if (holding && !Set.of(BookingStatus.ASSIGNED, BookingStatus.PARTNER_EN_ROUTE)
				.contains(booking.getBookingStatus())) {
			throw new InvalidRequestException("The partner can no longer be taken off a booking that is "
					+ booking.getBookingStatus() + ".");
		}

		assignment.setAssignmentStatus(AssignmentStatus.CANCELLED);
		assignment.setUpdatedBy(actingUserId);
		if (holding) {
			releasePartner(booking,
					Optional.ofNullable(NexHomeText.trimToNull(request.reason())).orElse(PARTNER_UNASSIGNED),
					actingUserId);
			tell(assignment.getPartner().getUser().getId(), "Taken off " + booking.getPublicId(),
					"You are no longer assigned to " + booking.getServiceNameSnapshot() + ".", booking, actingUserId);
			auditRecorder.record("PARTNER_UNASSIGNED", "BOOKING", booking.getPublicId(),
					Map.of("partner", assignment.getPartner().getPublicId()), null, actingUserId);
		}
		log.info("Assignment {} of booking {} cancelled by User ID {}", assignment.getId(), booking.getPublicId(),
				actingUserId);
		return bookingMapper.toAssignmentDto(assignment);
	}

	/**
	 * The partner takes the booking, ASSIGNED by staff (booking ASSIGNED: the partner accepts later) or ACCEPTED by the
	 * partner (booking PARTNER_EN_ROUTE, shown as "Partner Accepted"): the slot is confirmed, other offers end.
	 */
	private void take(NexHomeBookingAssignment assignment, String status, Long actingUserId) {
		NexHomeBooking booking = assignment.getBooking();
		NexHomePartnerProfile partner = assignment.getPartner();
		assignment.setAssignmentStatus(status);
		assignment.setRespondedAt(AssignmentStatus.ACCEPTED.equals(status) ? Instant.now() : null);
		assignment.setExpiresAt(null);
		assignment.setUpdatedBy(actingUserId);
		assignmentRepository.findByBookingIdAndAssignmentStatus(booking.getId(), AssignmentStatus.OFFERED).stream()
				.filter(other -> !other.getId().equals(assignment.getId()))
				.forEach(other -> {
					other.setAssignmentStatus(AssignmentStatus.CANCELLED);
					other.setUpdatedBy(actingUserId);
				});
		booking.setPartner(partner);
		changeStatus(booking, AssignmentStatus.ACCEPTED.equals(status) ? BookingStatus.PARTNER_EN_ROUTE
				: BookingStatus.ASSIGNED, null, actingUserId);
		log.info("Booking {} assigned to partner {}", booking.getPublicId(), partner.getPublicId());
	}

	/**
	 * The partner answers an assignment staff made: accepting makes it ACCEPTED and the booking PARTNER_EN_ROUTE
	 * ("Partner Accepted"); declining (a reason) takes the partner off the booking, as unassigning does.
	 */
	private NexHomeBookingAssignmentResponse answerAssignment(NexHomeBookingAssignment assignment,
			NexHomeAssignmentRespondRequest request, Long actingUserId) {
		NexHomeBooking booking = assignment.getBooking();
		if (!Set.of(BookingStatus.ASSIGNED, BookingStatus.PARTNER_EN_ROUTE).contains(booking.getBookingStatus())) {
			throw new InvalidRequestException("The booking is " + booking.getBookingStatus()
					+ " and the assignment can no longer be answered.");
		}
		assignment.setRespondedAt(Instant.now());
		assignment.setUpdatedBy(actingUserId);
		if (Boolean.TRUE.equals(request.accept())) {
			assignment.setAssignmentStatus(AssignmentStatus.ACCEPTED);
			if (BookingStatus.ASSIGNED.equals(booking.getBookingStatus())) {
				changeStatus(booking, BookingStatus.PARTNER_EN_ROUTE, null, actingUserId);
			}
			partnerAccepted(assignment, actingUserId);
			log.info("Partner {} accepted booking {}", assignment.getPartner().getPublicId(), booking.getPublicId());
			return bookingMapper.toAssignmentDto(assignment);
		}
		String reason = NexHomeText.trimToNull(request.rejectionReason());
		if (reason == null) {
			throw new InvalidRequestException("Rejection reason is required to reject an offer.");
		}
		assignment.setAssignmentStatus(AssignmentStatus.REJECTED);
		assignment.setRejectionReason(reason);
		releasePartner(booking, "Partner declined: " + reason, actingUserId);
		partnerDeclined(assignment, reason, actingUserId);
		log.info("Partner {} declined booking {}", assignment.getPartner().getPublicId(), booking.getPublicId());
		return bookingMapper.toAssignmentDto(assignment);
	}

	/**
	 * Back to waiting for a partner (CONFIRMED); an INSTANT booking's allotted slot goes with the partner (a SCHEDULED
	 * one keeps the time the customer chose).
	 */
	private void releasePartner(NexHomeBooking booking, String reason, Long actingUserId) {
		booking.setPartner(null);
		if (BookingType.INSTANT.equals(booking.getBookingType())) {
			booking.setScheduledStartAt(null);
			booking.setScheduledEndAt(null);
		}
		changeStatus(booking, BookingStatus.CONFIRMED, reason, actingUserId);
	}

	@Override
	@Transactional
	public int expireOffers() {
		List<NexHomeBookingAssignment> expired = assignmentRepository
				.findByAssignmentStatusAndExpiresAtLessThanEqual(AssignmentStatus.OFFERED, Instant.now());
		expired.forEach(assignment -> assignment.setAssignmentStatus(AssignmentStatus.EXPIRED));
		if (!expired.isEmpty()) {
			// Their slot holds ran out at the same time and are marked EXPIRED by the availability job
			log.info("{} booking offer(s) expired", expired.size());
		}
		return expired.size();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Timer and location
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomeBookingDetailResponse recordTimerEvent(NexHomeBookingTimerRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeBooking booking = findBooking(request.bookingId());
		if (!isAssignedPartner(booking) && !permissionService.can(BOOKING_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Only the booking's partner, or staff, can run its timer.");
		}
		String event = lookupService.validateCode(Types.BOOKING_TIMER_EVENT, request.eventType(), "Event type");
		String current = booking.getBookingStatus();
		if (!TIMER_FROM.getOrDefault(event, Set.of()).contains(current)) {
			throw new InvalidRequestException("The timer cannot " + event + " a booking that is " + current + ".");
		}
		Instant now = Instant.now();

		NexHomeBookingTimeLog timeLog = new NexHomeBookingTimeLog();
		timeLog.setBooking(booking);
		timeLog.setEventType(event);
		timeLog.setEventAt(now);
		timeLog.setRecordedBy(actingUserId);
		timeLog.setNotes(NexHomeText.trimToNull(request.notes()));
		timeLog.setCreatedBy(actingUserId);
		timeLog.setUpdatedBy(actingUserId);
		timeLogRepository.save(timeLog);

		if (BookingTimerEvent.START.equals(event)) {
			booking.setActualStartAt(now);
			// Starting the work also accepts an assignment the partner had not answered yet
			assignmentRepository.findFirstByBookingIdAndAssignmentStatusIn(booking.getId(), List.copyOf(HOLDING))
					.ifPresent(assignment -> {
						assignment.setAssignmentStatus(AssignmentStatus.STARTED);
						if (assignment.getRespondedAt() == null) {
							assignment.setRespondedAt(now);
						}
						assignment.setUpdatedBy(actingUserId);
					});
		}
		if (BookingTimerEvent.STOP.equals(event)) {
			booking.setActualEndAt(now);
			finish(booking, actingUserId);
		}
		changeStatus(booking, TIMER_TO.get(event), null, actingUserId);
		if (BookingTimerEvent.STOP.equals(event)) {
			// Cash is now due to the partner, or the partner is owed a payout for a paid online booking
			settlement.onBookingCompleted(booking, actingUserId);
			tell(customerUserOf(booking), "Booking " + booking.getPublicId() + " completed",
					booking.getServiceNameSnapshot() + " is done. Rate your service and partner.", booking,
					actingUserId);
		}
		log.info("Booking {} timer {} by User ID {}", booking.getPublicId(), event, actingUserId);
		return detailOf(booking);
	}

	/**
	 * The work is done: the assignment is complete, and an HOURLY job is priced again for the time
	 * worked (rounded and raised as the catalogue's quote does).
	 */
	private void finish(NexHomeBooking booking, Long actingUserId) {
		assignmentRepository.findFirstByBookingIdAndAssignmentStatusIn(booking.getId(),
				List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.ACCEPTED, AssignmentStatus.STARTED))
				.ifPresent(assignment -> {
					assignment.setAssignmentStatus(AssignmentStatus.COMPLETED);
					assignment.setUpdatedBy(actingUserId);
				});
		Map<String, Object> pricing = bookingMapper.readObject(booking.getPricingSnapshot());
		if (!PricingType.HOURLY.equals(pricing.get("pricingType"))) {
			return;
		}
		long worked = Math.max(1, workedMinutes(timeLogRepository.findByBookingIdOrderByEventAtAscIdAsc(
				booking.getId())));
		NexHomeServiceQuoteResponse quote = catalogService.quote(booking.getService().getId(),
				booking.getVariant() == null ? null : booking.getVariant().getId(), (int) Math.min(worked, 10080));
		Map<String, Object> repriced = new LinkedHashMap<>(NexHomeBookingCreator.pricingOf(quote,
				booking.getVariant()));
		repriced.put("workedMinutes", worked);
		repriced.put("bookedTotal", pricing.get("total"));
		// The fee at the rate the booking was made with
		Object rate = pricing.get("transactionFeePercentage");
		BigDecimal fee = NexHomeBookingCreator.feeOf(quote.total(),
				rate == null ? BigDecimal.ZERO : new BigDecimal(rate.toString()));
		repriced.put("transactionFeePercentage", rate);
		booking.setPricingSnapshot(bookingMapper.toJson(repriced));
		booking.setSubtotalAmount(quote.regularSubtotal());
		booking.setDiscountAmount(quote.regularSubtotal().subtract(quote.subtotal()));
		booking.setTransactionFeeAmount(fee);
		booking.setTotalAmount(quote.total().add(fee));
		List<NexHomeBookingItem> items = bookingItemRepository.findByBookingIdOrderByIdAsc(booking.getId());
		if (items.size() == 1) {
			items.get(0).setUnitPrice(quote.unitPrice());
			items.get(0).setLineTotal(quote.total());
			items.get(0).setUpdatedBy(actingUserId);
		}
	}

	/** Minutes between each START / RESUME and the PAUSE / STOP after it (an open run counts up to now). */
	static long workedMinutes(List<NexHomeBookingTimeLog> logs) {
		long seconds = 0;
		Instant runningSince = null;
		for (NexHomeBookingTimeLog log : logs) {
			switch (log.getEventType()) {
				case BookingTimerEvent.START, BookingTimerEvent.RESUME -> runningSince = log.getEventAt();
				case BookingTimerEvent.PAUSE, BookingTimerEvent.STOP -> {
					if (runningSince != null) {
						seconds += Duration.between(runningSince, log.getEventAt()).toSeconds();
						runningSince = null;
					}
				}
				default -> {
					// No other events
				}
			}
		}
		if (runningSince != null) {
			seconds += Duration.between(runningSince, Instant.now()).toSeconds();
		}
		return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 0, RoundingMode.UP).longValue();
	}

	@Override
	@Transactional
	public NexHomePartnerLocationResponse updateLocation(NexHomePartnerLocationUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeBooking booking = findBooking(request.bookingId());
		if (!isAssignedPartner(booking)) {
			throw new AccessDeniedException("Only the booking's partner shares their location.");
		}
		if (!TRACKED.contains(booking.getBookingStatus())) {
			throw new InvalidRequestException("Location is shared only while the booking is under way.");
		}
		NexHomePartnerLocationUpdate location = new NexHomePartnerLocationUpdate();
		location.setPartner(booking.getPartner());
		location.setBooking(booking);
		location.setLatitude(request.latitude().setScale(7, RoundingMode.HALF_UP));
		location.setLongitude(request.longitude().setScale(7, RoundingMode.HALF_UP));
		location.setRecordedAt(Instant.now());
		location.setCreatedBy(actingUserId);
		location.setUpdatedBy(actingUserId);
		return bookingMapper.toLocationDto(locationRepository.save(location));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePartnerLocationResponse getLatestLocation(Long bookingId) {
		NexHomeBooking booking = findBooking(bookingId);
		requireCanSee(booking);
		return locationRepository.findFirstByBookingIdOrderByRecordedAtDescIdDesc(bookingId)
				.map(bookingMapper::toLocationDto)
				.orElse(null);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------------------------------------------------

	/** Sets the status and writes its history row in the same transaction. */
	/** A booking notification to one user (none for a null user, the actor or the system account). */
	private void tell(Long userId, String title, String message, NexHomeBooking booking, Long actingUserId) {
		notifier.notify(userId, new NexHomeNotifier.Message(NotificationType.BOOKING, title, message,
				NexHomeNotifier.BOOKING, booking.getId()), actingUserId);
	}

	private static Long customerUserOf(NexHomeBooking booking) {
		return booking.getCustomer().getUser().getId();
	}

	private static Long partnerUserOf(NexHomeBooking booking) {
		return booking.getPartner() == null ? null : booking.getPartner().getUser().getId();
	}

	/** The customer hears the partner accepted. */
	private void partnerAccepted(NexHomeBookingAssignment assignment, Long actingUserId) {
		NexHomeBooking booking = assignment.getBooking();
		tell(customerUserOf(booking), "Partner accepted " + booking.getPublicId(),
				"Your partner accepted the job for " + booking.getServiceNameSnapshot() + ".", booking, actingUserId);
	}

	/** The staff who read bookings hear the partner declined, so they find another. */
	private void partnerDeclined(NexHomeBookingAssignment assignment, String reason, Long actingUserId) {
		NexHomeBooking booking = assignment.getBooking();
		notifier.notifyStaff(BOOKING_MANAGE, new NexHomeNotifier.Message(NotificationType.BOOKING,
				"Partner declined " + booking.getPublicId(),
				assignment.getPartner().getPublicId() + " declined: " + reason, NexHomeNotifier.BOOKING,
				booking.getId()), actingUserId);
	}

	private void changeStatus(NexHomeBooking booking, String target, String reason, Long actingUserId) {
		String current = booking.getBookingStatus();
		booking.setBookingStatus(target);
		booking.setUpdatedBy(actingUserId);
		bookingCreator.recordStatus(booking, current, target, reason, actingUserId);
	}

	private NexHomeBookingDetailResponse detailOf(NexHomeBooking booking) {
		Long bookingId = booking.getId();
		List<NexHomeBookingStatusHistory> history = historyRepository
				.findByBookingIdOrderByCreationDateAscIdAsc(bookingId);
		List<NexHomeAuditUsers.Line> lines = history.stream()
				.map(row -> new NexHomeAuditUsers.Line(row.getNewStatus(), row.getOldStatus(),
						row.getCreationDate() != null ? row.getCreationDate() : Instant.now(), row.getChangedBy()))
				.toList();
		List<NexHomeAuditEntry> who = auditUsers.entries(lines);
		List<NexHomeBookingHistoryResponse> historyDtos = new ArrayList<>();
		for (int i = 0; i < history.size(); i++) {
			NexHomeBookingStatusHistory row = history.get(i);
			historyDtos.add(new NexHomeBookingHistoryResponse(row.getId(), row.getOldStatus(), row.getNewStatus(),
					row.getChangedBy(), who.get(i).byName(), who.get(i).byRole(), row.getReason(),
					lines.get(i).at()));
		}
		List<NexHomeBookingTimeLog> logs = timeLogRepository.findByBookingIdOrderByEventAtAscIdAsc(bookingId);
		// Who assigned each partner, by name and role (one query for all, as for the history)
		List<NexHomeBookingAssignment> assignments = assignmentRepository
				.findByBookingIdOrderByOfferedAtDescIdDesc(bookingId);
		List<NexHomeAuditEntry> assignedBy = auditUsers.entries(assignments.stream()
				.map(row -> new NexHomeAuditUsers.Line(row.getAssignmentStatus(), null,
						row.getOfferedAt() != null ? row.getOfferedAt() : Instant.now(), row.getAssignedBy()))
				.toList());
		List<NexHomeBookingAssignmentResponse> assignmentDtos = new ArrayList<>();
		for (int i = 0; i < assignments.size(); i++) {
			assignmentDtos.add(bookingMapper.toAssignmentDto(assignments.get(i))
					.withAssignedBy(assignedBy.get(i).byName(), assignedBy.get(i).byRole()));
		}
		NexHomeServiceCategory category = booking.getService().getCategory();
		return new NexHomeBookingDetailResponse(
				bookingMapper.toBookingDto(booking),
				category.getId(),
				category.getName(),
				bookingItemRepository.findByBookingIdOrderByIdAsc(bookingId).stream()
						.map(bookingMapper::toBookingItemDto).toList(),
				historyDtos,
				assignmentDtos,
				logs.stream().map(bookingMapper::toTimeLogDto).toList(),
				workedMinutes(logs),
				locationRepository.findFirstByBookingIdOrderByRecordedAtDescIdDesc(bookingId)
						.map(bookingMapper::toLocationDto).orElse(null));
	}

	/** An approved partner with an active account who offers the service (active). */
	private void requireCanTake(NexHomePartnerProfile partner, Long serviceId) {
		if (!PartnerOnboardingStatus.APPROVED.equals(partner.getOnboardingStatus())) {
			throw new InvalidRequestException("Only an APPROVED partner can be booked.");
		}
		if (!AccountStatus.ACTIVE.equals(partner.getUser().getAccountStatus())) {
			throw new InvalidRequestException("The partner's account is not active.");
		}
		boolean offers = partnerServiceRepository.findByPartnerIdOrderByPrimaryServiceDescCreationDateDescIdDesc(
				partner.getId()).stream()
				.anyMatch(offered -> offered.isActive() && offered.getService().getId().equals(serviceId));
		if (!offers) {
			throw new InvalidRequestException("The partner does not offer this service.");
		}
	}

	/** The end of the booking's time (its start plus an hour when it has no end). */
	/**
	 * Staff allot the slot when offering: it becomes the booking's scheduled start and end. Not while other offers
	 * hold the partners' time for the old slot.
	 */
	private void allotSlot(NexHomeBooking booking, Instant startsAt, Instant endsAt, Long actingUserId) {
		if (startsAt == null || endsAt == null) {
			throw new InvalidRequestException("Give both the slot's start and end.");
		}
		if (!endsAt.isAfter(startsAt)) {
			throw new InvalidRequestException("The slot must end after it starts.");
		}
		if (startsAt.equals(booking.getScheduledStartAt()) && endsAt.equals(booking.getScheduledEndAt())) {
			return;
		}
		if (!assignmentRepository.findByBookingIdAndAssignmentStatus(booking.getId(), AssignmentStatus.OFFERED)
				.isEmpty()) {
			throw new InvalidRequestException("Withdraw the open offers before changing the booking's slot.");
		}
		booking.setScheduledStartAt(startsAt);
		booking.setScheduledEndAt(endsAt);
		booking.setUpdatedBy(actingUserId);
		log.info("Booking {} allotted the slot {} to {} by User ID {}", booking.getPublicId(), startsAt, endsAt,
				actingUserId);
	}

	private static Instant endOf(NexHomeBooking booking) {
		return booking.getScheduledEndAt() != null ? booking.getScheduledEndAt()
				: booking.getScheduledStartAt().plus(Duration.ofMinutes(NexHomeBookingCreator.DEFAULT_DURATION_MINUTES));
	}

	private void requireCanSee(NexHomeBooking booking) {
		if (isCustomer(booking) || isAssignedPartner(booking) || isOfferedPartner(booking)
				|| permissionService.can(BOOKING_MANAGE, NexHomeAccess.READ.name())) {
			return;
		}
		throw new AccessDeniedException("Not the booking's customer or partner, and no " + BOOKING_MANAGE
				+ " READ access.");
	}

	private boolean isCustomer(NexHomeBooking booking) {
		return booking.getCustomer().getUser().getId().equals(permissionService.currentUserId());
	}

	private boolean isAssignedPartner(NexHomeBooking booking) {
		return booking.getPartner() != null
				&& booking.getPartner().getUser().getId().equals(permissionService.currentUserId());
	}

	private boolean isOfferedPartner(NexHomeBooking booking) {
		return myPartnerProfile()
				.map(partner -> assignmentRepository.existsByBookingIdAndPartnerIdAndAssignmentStatus(booking.getId(),
						partner.getId(), AssignmentStatus.OFFERED))
				.orElse(false);
	}

	private Optional<NexHomePartnerProfile> myPartnerProfile() {
		return partnerProfileRepository.findByUserId(permissionService.currentUserId());
	}

	private String optionalCode(String typeCode, String code, String fieldName) {
		String trimmed = NexHomeText.trimToNull(code);
		return trimmed == null ? null : lookupService.validateCode(typeCode, trimmed, fieldName);
	}

	private NexHomeBooking findBooking(Long bookingId) {
		return bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking does not exist."));
	}

	private NexHomeBookingAssignment findAssignment(Long assignmentId) {
		return assignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new ResourceNotFoundException("Assignment does not exist."));
	}
}
