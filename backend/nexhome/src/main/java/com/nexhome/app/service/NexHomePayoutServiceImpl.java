package com.nexhome.app.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePayoutAdjustRequest;
import com.nexhome.app.dto.NexHomePayoutResponse;
import com.nexhome.app.dto.NexHomePayoutStatsResponse;
import com.nexhome.app.dto.NexHomePayoutStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeTransferResponse;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeBookingMapper;
import com.nexhome.app.mapper.NexHomePaymentMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeLookupCodes.NotificationType;
import com.nexhome.app.model.NexHomeLookupCodes.PayoutStatus;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomePartnerPayout;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.repository.NexHomePartnerPayoutRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerTransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomePayoutServiceImpl implements NexHomePayoutService {

	/** Where staff may move a payout (V62: partners are paid outside the app and recorded as manual payouts). */
	private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
			PayoutStatus.PENDING, Set.of(PayoutStatus.ON_HOLD, PayoutStatus.CANCELLED),
			PayoutStatus.ON_HOLD, Set.of(PayoutStatus.PENDING, PayoutStatus.CANCELLED),
			PayoutStatus.FAILED, Set.of(PayoutStatus.PENDING, PayoutStatus.CANCELLED));

	private final NexHomePartnerPayoutRepository payoutRepository;
	private final NexHomePartnerTransferRepository transferRepository;
	private final NexHomePartnerProfileRepository partnerProfileRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomePaymentMapper paymentMapper;
	private final NexHomeBookingMapper bookingMapper;
	private final NexHomeNotifier notifier;
	private final NexHomeAuditRecorder auditRecorder;
	private final com.nexhome.app.repository.NexHomeBookingRepository bookingRepository;
	private final com.nexhome.app.repository.NexHomeCashCollectionRepository cashCollectionRepository;
	private final com.nexhome.app.repository.NexHomePartnerManualPayoutRepository manualPayoutRepository;
	private final com.nexhome.app.repository.NexHomePartnerDeductionRepository deductionRepository;
	private final NexHomeAuditUsers auditUsers;

	private static final java.time.ZoneId ZONE = java.time.ZoneId.of("Asia/Kolkata");
	/** The periods of a partner's earnings. */
	private static final java.util.List<String> PERIODS = java.util.List.of("TODAY", "WEEK", "MONTH", "ALL");

	@Override
	@Transactional(readOnly = true)
	public com.nexhome.app.dto.NexHomePartnerEarningsResponse getPartnerEarnings(Long partnerProfileId, String period,
			Integer page, Integer size) {
		NexHomePartnerProfile partner = partnerProfileRepository.findById(partnerProfileId)
				.orElseThrow(() -> new ResourceNotFoundException("Partner profile does not exist."));
		if (!partner.getUser().getId().equals(permissionService.currentUserId())
				&& !permissionService.can("PAYOUT_MANAGE", com.nexhome.app.model.NexHomeAccess.READ.name())) {
			throw new org.springframework.security.access.AccessDeniedException(
					"You do not have permission to see this partner's earnings.");
		}
		String chosen = period == null ? "TODAY" : period.trim().toUpperCase();
		if (!PERIODS.contains(chosen)) {
			throw new InvalidRequestException("Period must be one of: TODAY, WEEK, MONTH, ALL.");
		}
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.unsorted());

		// The period starts at midnight IST: today, this week's Monday, or the 1st of this month
		java.time.LocalDate today = java.time.LocalDate.now(ZONE);
		java.time.LocalDate start = switch (chosen) {
			case "TODAY" -> today;
			case "WEEK" -> today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
			case "MONTH" -> today.withDayOfMonth(1);
			default -> null;
		};
		Instant from = start == null ? null : start.atStartOfDay(ZONE).toInstant();
		String completed = com.nexhome.app.model.NexHomeLookupCodes.BookingStatus.COMPLETED;
		java.util.List<com.nexhome.app.model.NexHomeBooking> bookings = from == null
				? bookingRepository.findByPartnerIdAndBookingStatusOrderByActualEndAtDescIdDesc(partnerProfileId,
						completed)
				: bookingRepository.findByPartnerIdAndBookingStatusAndActualEndAtGreaterThanEqualOrderByActualEndAtDescIdDesc(
						partnerProfileId, completed, from);

		// Every job's figures (one query each for payouts and cash), then the totals and the requested page
		java.util.List<Long> ids = bookings.stream().map(com.nexhome.app.model.NexHomeBooking::getId).toList();
		Map<Long, NexHomePartnerPayout> payouts = new java.util.HashMap<>();
		Map<Long, String> cash = new java.util.HashMap<>();
		if (!ids.isEmpty()) {
			payoutRepository.findByBookingIdIn(ids).forEach(payout -> payouts.put(payout.getBooking().getId(), payout));
			cashCollectionRepository.findByBookingIdIn(ids)
					.forEach(collection -> cash.put(collection.getBooking().getId(), collection.getStatus()));
		}
		java.util.List<com.nexhome.app.dto.NexHomePartnerEarningsResponse.Record> records = bookings.stream()
				.map(booking -> {
					NexHomePartnerPayout payout = payouts.get(booking.getId());
					BigDecimal total;
					BigDecimal commission;
					BigDecimal earnings;
					if (payout != null) {
						total = payout.getGrossAmount();
						commission = payout.getPlatformCommission();
						earnings = payout.getNetAmount();
					}
					else {
						total = booking.getTotalAmount().subtract(booking.getTransactionFeeAmount());
						commission = NexHomeBookingMapper.commissionOf(booking.getService(), total);
						earnings = total.subtract(commission);
					}
					String cashStatus = cash.get(booking.getId());
					return new com.nexhome.app.dto.NexHomePartnerEarningsResponse.Record(booking.getId(),
							booking.getPublicId(), booking.getServiceNameSnapshot(), booking.getActualEndAt(),
							booking.getPaymentMethod(), total, commission, earnings,
							payout != null ? "PAYOUT" : cashStatus != null ? "CASH" : null,
							payout != null ? payout.getPayoutStatus() : cashStatus);
				})
				.toList();
		BigDecimal totalAmount = sum(records, com.nexhome.app.dto.NexHomePartnerEarningsResponse.Record::totalAmount);
		BigDecimal commission = sum(records, com.nexhome.app.dto.NexHomePartnerEarningsResponse.Record::commission);
		BigDecimal earnings = sum(records, com.nexhome.app.dto.NexHomePartnerEarningsResponse.Record::partnerEarnings);
		// Not paid by the customer yet: an online job without a payout (a cash job completes only once collected)
		java.util.List<com.nexhome.app.dto.NexHomePartnerEarningsResponse.Record> pending = records.stream()
				.filter(record -> record.settlement() == null).toList();
		BigDecimal pendingEarnings = sum(pending,
				com.nexhome.app.dto.NexHomePartnerEarningsResponse.Record::partnerEarnings);
		int first = (int) Math.min(pageRequest.getOffset(), records.size());
		int last = Math.min(first + pageRequest.getPageSize(), records.size());
		org.springframework.data.domain.Page<com.nexhome.app.dto.NexHomePartnerEarningsResponse.Record> pageOfRecords =
				new org.springframework.data.domain.PageImpl<>(records.subList(first, last), pageRequest,
						records.size());
		return new com.nexhome.app.dto.NexHomePartnerEarningsResponse(chosen, from, records.size(), totalAmount,
				commission, earnings, earnings.subtract(pendingEarnings), pendingEarnings, pending.size(),
				NexHomePaging.toPageResponse(pageRequest, pageOfRecords,
						pageOfRecords.getContent()));
	}

	private static BigDecimal sum(java.util.List<com.nexhome.app.dto.NexHomePartnerEarningsResponse.Record> records,
			java.util.function.Function<com.nexhome.app.dto.NexHomePartnerEarningsResponse.Record, BigDecimal> amount) {
		return records.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Payouts
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomePayoutResponse> getPayouts(Integer page, Integer size, String payoutStatus,
			Long partnerProfileId) {
		String status = optionalCode(Types.PAYOUT_STATUS, payoutStatus, "Payout status");
		Specification<NexHomePartnerPayout> spec = (payout, query, cb) -> cb.conjunction();
		if (status != null) {
			spec = spec.and((payout, query, cb) -> cb.equal(payout.get("payoutStatus"), status));
		}
		if (partnerProfileId != null) {
			spec = spec.and((payout, query, cb) -> cb.equal(payout.get("partner").get("id"), partnerProfileId));
		}
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomePartnerPayout> payouts = payoutRepository.findAll(spec, pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, payouts,
				payouts.stream().map(paymentMapper::toPayoutDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomePayoutResponse> getMyPayouts(Integer page, Integer size) {
		NexHomePartnerProfile partner = partnerProfileRepository.findByUserId(permissionService.currentUserId())
				.orElseThrow(() -> new ResourceNotFoundException("You do not have a partner profile."));
		return getPayouts(page, size, null, partner.getId());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePayoutStatsResponse getPayoutStats() {
		Map<String, Long> counts = new LinkedHashMap<>();
		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		for (Object[] row : payoutRepository.totalsByStatus()) {
			counts.put((String) row[0], ((Number) row[1]).longValue());
			totals.put((String) row[0], (BigDecimal) row[2]);
		}
		return new NexHomePayoutStatsResponse(counts, totals);
	}

	@Override
	@Transactional
	public NexHomePayoutResponse updateStatus(NexHomePayoutStatusUpdateRequest request) {
		NexHomePartnerPayout payout = findPayout(request.payoutId());
		String target = lookupService.validateCode(Types.PAYOUT_STATUS, request.payoutStatus(), "Payout status");
		if (!STATUS_TRANSITIONS.getOrDefault(payout.getPayoutStatus(), Set.of()).contains(target)) {
			throw new InvalidRequestException("A payout that is " + payout.getPayoutStatus() + " cannot be moved to "
					+ target + ".");
		}
		auditRecorder.record("STATUS_CHANGED", "PAYOUT", payout.getPublicId(),
				Map.of("payoutStatus", payout.getPayoutStatus()), Map.of("payoutStatus", target),
				permissionService.currentUserId());
		payout.setPayoutStatus(target);
		payout.setUpdatedBy(permissionService.currentUserId());
		log.info("Payout {} is now {}", payout.getPublicId(), target);
		return paymentMapper.toPayoutDto(payout);
	}

	@Override
	@Transactional
	public NexHomePayoutResponse adjust(NexHomePayoutAdjustRequest request) {
		NexHomePartnerPayout payout = findPayout(request.payoutId());
		if (!NexHomeSettlement.OPEN_PAYOUTS.contains(payout.getPayoutStatus())) {
			throw new InvalidRequestException("A payout that is " + payout.getPayoutStatus() + " cannot be adjusted.");
		}
		BigDecimal net = payout.getGrossAmount().subtract(payout.getPlatformCommission())
				.add(request.adjustmentsAmount());
		if (net.signum() < 0) {
			throw new InvalidRequestException("The adjustment would make the payout negative; at most "
					+ payout.getGrossAmount().subtract(payout.getPlatformCommission()).toPlainString()
					+ " can be deducted.");
		}
		auditRecorder.record("ADJUSTED", "PAYOUT", payout.getPublicId(),
				Map.of("adjustmentsAmount", payout.getAdjustmentsAmount(), "netAmount", payout.getNetAmount()),
				Map.of("adjustmentsAmount", request.adjustmentsAmount(), "netAmount", net),
				permissionService.currentUserId());
		payout.setAdjustmentsAmount(request.adjustmentsAmount());
		payout.setNetAmount(net);
		payout.setUpdatedBy(permissionService.currentUserId());
		return paymentMapper.toPayoutDto(payout);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeTransferResponse> getTransfers(Integer page, Integer size) {
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.unsorted());
		var transfers = transferRepository.findAllByOrderByCreationDateDescIdDesc(pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, transfers,
				transfers.stream().map(paymentMapper::toTransferDto).toList());
	}

	// ------------------------------------------------------------------------------------------------------------
	// What NexHome owes partners (V62): partners hand over the cash of cash jobs, so every completed job counts
	// (cash and online, less commission); staff pay outside the app and record each payment
	// ------------------------------------------------------------------------------------------------------------

	private NexHomePartnerProfile partnerForOwnerOrReader(Long partnerProfileId) {
		NexHomePartnerProfile partner = partnerProfileRepository.findById(partnerProfileId)
				.orElseThrow(() -> new ResourceNotFoundException("Partner profile does not exist."));
		requireOwnerOrReader(partner);
		return partner;
	}

	private void requireOwnerOrReader(NexHomePartnerProfile partner) {
		if (!partner.getUser().getId().equals(permissionService.currentUserId())
				&& !permissionService.can("PAYOUT_MANAGE", com.nexhome.app.model.NexHomeAccess.READ.name())) {
			throw new org.springframework.security.access.AccessDeniedException(
					"You do not have permission to see this partner's payouts.");
		}
	}

	/** What a partner has earned and been paid, with the jobs. */
	private com.nexhome.app.dto.NexHomePartnerWalletResponse walletOf(NexHomePartnerProfile partner) {
		java.util.List<com.nexhome.app.dto.NexHomePartnerWalletResponse.Item> items = new java.util.ArrayList<>();
		BigDecimal onlineAmount = BigDecimal.ZERO;
		BigDecimal onlineCommission = BigDecimal.ZERO;
		BigDecimal adjustments = BigDecimal.ZERO;
		java.util.List<NexHomePartnerPayout> payouts = payoutRepository.findOwedOfPartner(partner.getId());
		for (NexHomePartnerPayout payout : payouts) {
			com.nexhome.app.model.NexHomeBooking booking = payout.getBooking();
			onlineAmount = onlineAmount.add(payout.getGrossAmount());
			onlineCommission = onlineCommission.add(payout.getPlatformCommission());
			adjustments = adjustments.add(payout.getAdjustmentsAmount());
			items.add(new com.nexhome.app.dto.NexHomePartnerWalletResponse.Item(booking.getId(),
					booking.getPublicId(), booking.getServiceNameSnapshot(), booking.getActualEndAt(),
					booking.getPaymentMethod(), payout.getGrossAmount(), payout.getPlatformCommission(),
					payout.getAdjustmentsAmount(), payout.getNetAmount()));
		}
		BigDecimal cashAmount = BigDecimal.ZERO;
		BigDecimal cashCommission = BigDecimal.ZERO;
		java.util.List<com.nexhome.app.model.NexHomeCashCollection> cash = cashCollectionRepository
				.findCollectedByPartnerWithService(partner.getId());
		for (com.nexhome.app.model.NexHomeCashCollection collection : cash) {
			com.nexhome.app.model.NexHomeBooking booking = collection.getBooking();
			BigDecimal commission = NexHomeBookingMapper.commissionOf(booking.getService(), collection.getAmount());
			cashAmount = cashAmount.add(collection.getAmount());
			cashCommission = cashCommission.add(commission);
			items.add(new com.nexhome.app.dto.NexHomePartnerWalletResponse.Item(booking.getId(),
					booking.getPublicId(), booking.getServiceNameSnapshot(), booking.getActualEndAt(),
					booking.getPaymentMethod(), collection.getAmount(), commission, BigDecimal.ZERO,
					collection.getAmount().subtract(commission)));
		}
		// Latest job first
		items.sort(java.util.Comparator.comparing(com.nexhome.app.dto.NexHomePartnerWalletResponse.Item::completedAt,
				java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
		BigDecimal totalAmount = cashAmount.add(onlineAmount);
		BigDecimal totalCommission = cashCommission.add(onlineCommission);
		BigDecimal earned = totalAmount.subtract(totalCommission).add(adjustments);
		BigDecimal paid = manualPayoutRepository.sumPaidOfPartner(partner.getId());
		BigDecimal deducted = deductionRepository.sumDeductedOfPartner(partner.getId());
		var last = manualPayoutRepository.findFirstByPartnerIdAndPayoutStatusOrderByPaidOnDescIdDesc(partner.getId(),
				com.nexhome.app.model.NexHomePartnerManualPayout.RECORDED);
		return new com.nexhome.app.dto.NexHomePartnerWalletResponse(partner.getId(), cash.size(), cashAmount,
				cashCommission, payouts.size(), onlineAmount, onlineCommission, adjustments, totalAmount,
				totalCommission, earned, paid, manualPayoutRepository.countPaidOfPartner(partner.getId()), deducted,
				deductionRepository.countDeductedOfPartner(partner.getId()), earned.subtract(paid).subtract(deducted),
				last.map(found -> manualPayoutDtos(java.util.List.of(found)).get(0)).orElse(null),
				items.size(), items.subList(0, Math.min(items.size(),
						com.nexhome.app.dto.NexHomePartnerWalletResponse.MAX_ITEMS)));
	}

	@Override
	@Transactional(readOnly = true)
	public com.nexhome.app.dto.NexHomePartnerWalletResponse getWallet(Long partnerProfileId) {
		return walletOf(partnerForOwnerOrReader(partnerProfileId));
	}

	@Override
	@Transactional
	public com.nexhome.app.dto.NexHomeManualPayoutResponse recordManualPayout(
			com.nexhome.app.dto.NexHomeManualPayoutCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile partner = partnerProfileRepository.findById(request.partnerProfileId())
				.orElseThrow(() -> new ResourceNotFoundException("Partner profile does not exist."));
		String mode = lookupService.validateCode(Types.PAYOUT_MODE, request.paymentMode(), "Paid through");
		String transactionId = NexHomeText.trimToNull(request.transactionId());
		if (transactionId == null && !"CASH".equals(mode)) {
			throw new InvalidRequestException("Transaction ID is required unless the partner was paid in cash.");
		}
		if (request.paidOn().isAfter(java.time.LocalDate.now(ZONE))) {
			throw new InvalidRequestException("Payment date cannot be in the future.");
		}
		// A deduction recorded with it (V63): checked before anything is saved
		String deductionReason = null;
		String deductionNotes = null;
		if (request.deductionAmount() != null) {
			deductionReason = validDeductionReason(request.deductionReason());
			deductionNotes = deductionNotes(deductionReason, request.deductionNotes());
		}
		BigDecimal balance = walletOf(partner).balance();
		BigDecimal total = request.amount().add(request.deductionAmount() == null ? BigDecimal.ZERO
				: request.deductionAmount());
		if (total.compareTo(balance) > 0) {
			throw new InvalidRequestException(balance.signum() <= 0 ? "NexHome owes this partner nothing right now."
					: request.deductionAmount() == null
							? "The amount is more than NexHome owes the partner (" + balance.toPlainString() + ")."
							: "The payment and the deduction together are more than NexHome owes the partner ("
									+ balance.toPlainString() + ").");
		}

		com.nexhome.app.model.NexHomePartnerManualPayout payout = new com.nexhome.app.model.NexHomePartnerManualPayout();
		payout.setPartner(partner);
		payout.setAmount(request.amount());
		payout.setPaymentMode(mode);
		payout.setTransactionId(transactionId);
		payout.setPaidOn(request.paidOn());
		payout.setNotes(NexHomeText.trimToNull(request.notes()));
		payout.setCreatedBy(actingUserId);
		payout.setUpdatedBy(actingUserId);
		payout = manualPayoutRepository.saveAndFlush(payout);

		String modeName = lookupService.getActiveLookupValues(Types.PAYOUT_MODE).stream()
				.filter(value -> value.valueCode().equals(mode)).map(value -> value.displayName()).findFirst()
				.orElse(mode);
		notifier.notify(partner.getUser().getId(), new NexHomeNotifier.Message(NotificationType.PAYMENT,
				"NexHome paid you " + request.amount().toPlainString(),
				"Paid through " + modeName + (transactionId == null ? "" : ", transaction " + transactionId) + ".",
				NexHomeNotifier.PARTNER_PROFILE, partner.getId()), actingUserId);
		auditRecorder.record("PARTNER_PAID", "PARTNER_PAYOUT", payout.getPublicId(), null,
				NexHomeAuditRecorder.values("partner", partner.getPublicId(), "amount", request.amount(),
						"paymentMode", mode, "transactionId", transactionId, "paidOn", request.paidOn().toString()),
				actingUserId);
		log.info("Payout {} of {} to partner {} recorded", payout.getPublicId(), request.amount(),
				partner.getPublicId());
		if (request.deductionAmount() != null) {
			saveDeduction(partner, payout, request.deductionAmount(), deductionReason, deductionNotes,
					request.paidOn(), actingUserId);
		}
		return manualPayoutDtos(java.util.List.of(payout)).get(0);
	}

	@Override
	@Transactional
	public com.nexhome.app.dto.NexHomeManualPayoutResponse cancelManualPayout(
			com.nexhome.app.dto.NexHomeManualPayoutCancelRequest request) {
		Long actingUserId = permissionService.currentUserId();
		com.nexhome.app.model.NexHomePartnerManualPayout payout = manualPayoutRepository.findById(request.payoutId())
				.orElseThrow(() -> new ResourceNotFoundException("Payout does not exist."));
		if (com.nexhome.app.model.NexHomePartnerManualPayout.CANCELLED.equals(payout.getPayoutStatus())) {
			throw new InvalidRequestException("This payout is already cancelled.");
		}
		payout.setPayoutStatus(com.nexhome.app.model.NexHomePartnerManualPayout.CANCELLED);
		payout.setCancelledAt(Instant.now());
		payout.setCancelledBy(actingUserId);
		payout.setCancellationReason(request.reason().trim());
		payout.setUpdatedBy(actingUserId);
		auditRecorder.record("PARTNER_PAYOUT_CANCELLED", "PARTNER_PAYOUT", payout.getPublicId(),
				Map.of("payoutStatus", com.nexhome.app.model.NexHomePartnerManualPayout.RECORDED),
				Map.of("payoutStatus", com.nexhome.app.model.NexHomePartnerManualPayout.CANCELLED, "reason",
						request.reason().trim()),
				actingUserId);
		log.info("Payout {} cancelled", payout.getPublicId());
		return manualPayoutDtos(java.util.List.of(payout)).get(0);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<com.nexhome.app.dto.NexHomeManualPayoutResponse> getManualPayouts(
			Long partnerProfileId, Integer page, Integer size) {
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.unsorted());
		Page<com.nexhome.app.model.NexHomePartnerManualPayout> payouts;
		if (partnerProfileId == null) {
			if (!permissionService.can("PAYOUT_MANAGE", com.nexhome.app.model.NexHomeAccess.READ.name())) {
				throw new org.springframework.security.access.AccessDeniedException(
						"You do not have permission to see partner payouts.");
			}
			payouts = manualPayoutRepository.findAllNewestFirst(pageRequest);
		}
		else {
			payouts = manualPayoutRepository.findByPartnerNewestFirst(
					partnerForOwnerOrReader(partnerProfileId).getId(), pageRequest);
		}
		return NexHomePaging.toPageResponse(pageRequest, payouts, manualPayoutDtos(payouts.getContent()));
	}

	@Override
	@Transactional(readOnly = true)
	public com.nexhome.app.dto.NexHomeManualPayoutResponse getManualPayout(Long payoutId) {
		com.nexhome.app.model.NexHomePartnerManualPayout payout = manualPayoutRepository.findById(payoutId)
				.orElseThrow(() -> new ResourceNotFoundException("Payout does not exist."));
		requireOwnerOrReader(payout.getPartner());
		return manualPayoutDtos(java.util.List.of(payout)).get(0);
	}

	/** [jobs, earned] per partner, from every payout not cancelled and every COLLECTED cash job. */
	private Map<Long, Object[]> earnedByPartner() {
		Map<Long, Object[]> earned = new java.util.HashMap<>();
		for (Object[] row : payoutRepository.owedByPartner()) {
			earned.put((Long) row[0], new Object[] { ((Number) row[1]).longValue(), (BigDecimal) row[2] });
		}
		for (com.nexhome.app.model.NexHomeCashCollection collection : cashCollectionRepository
				.findAllCollectedWithService()) {
			com.nexhome.app.model.NexHomeBooking booking = collection.getBooking();
			BigDecimal share = collection.getAmount()
					.subtract(NexHomeBookingMapper.commissionOf(booking.getService(), collection.getAmount()));
			Object[] totals = earned.computeIfAbsent(booking.getPartner().getId(),
					id -> new Object[] { 0L, BigDecimal.ZERO });
			totals[0] = (Long) totals[0] + 1;
			totals[1] = ((BigDecimal) totals[1]).add(share);
		}
		return earned;
	}

	/** Every partner with jobs or payouts and what NexHome owes them, the largest balance first. */
	private java.util.List<com.nexhome.app.dto.NexHomePartnerDueResponse> allDues() {
		Map<Long, Object[]> earned = earnedByPartner();
		Map<Long, BigDecimal> paid = new java.util.HashMap<>();
		for (Object[] row : manualPayoutRepository.paidByPartner()) {
			paid.put((Long) row[0], (BigDecimal) row[1]);
		}
		Map<Long, BigDecimal> deducted = new java.util.HashMap<>();
		for (Object[] row : deductionRepository.deductedByPartner()) {
			deducted.put((Long) row[0], (BigDecimal) row[1]);
		}
		java.util.Set<Long> ids = new java.util.HashSet<>(earned.keySet());
		ids.addAll(paid.keySet());
		ids.addAll(deducted.keySet());
		java.util.List<com.nexhome.app.dto.NexHomePartnerDueResponse> dues = new java.util.ArrayList<>();
		for (NexHomePartnerProfile partner : partnerProfileRepository.findAllById(ids)) {
			Object[] totals = earned.getOrDefault(partner.getId(), new Object[] { 0L, BigDecimal.ZERO });
			BigDecimal paidTotal = paid.getOrDefault(partner.getId(), BigDecimal.ZERO);
			BigDecimal deductedTotal = deducted.getOrDefault(partner.getId(), BigDecimal.ZERO);
			String name = String.join(" ", java.util.stream.Stream.of(partner.getFirstName(), partner.getLastName())
					.filter(java.util.Objects::nonNull).toList()).trim();
			dues.add(new com.nexhome.app.dto.NexHomePartnerDueResponse(partner.getId(), partner.getPublicId(),
					name.isEmpty() ? null : name,
					partner.getUser().getCountryCode() + " " + partner.getUser().getMobileNumber(), (Long) totals[0],
					(BigDecimal) totals[1], paidTotal, deductedTotal,
					((BigDecimal) totals[1]).subtract(paidTotal).subtract(deductedTotal)));
		}
		dues.sort(java.util.Comparator.comparing(com.nexhome.app.dto.NexHomePartnerDueResponse::balance).reversed()
				.thenComparing(com.nexhome.app.dto.NexHomePartnerDueResponse::publicId));
		return dues;
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<com.nexhome.app.dto.NexHomePartnerDueResponse> getPartnerDues(Integer page,
			Integer size, String search, Boolean dueOnly) {
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.unsorted());
		String text = NexHomePaging.searchText(search);
		String digits = text == null ? "" : text.replaceAll("\\D", "");
		java.util.List<com.nexhome.app.dto.NexHomePartnerDueResponse> matching = allDues().stream()
				.filter(due -> !Boolean.TRUE.equals(dueOnly) || due.balance().signum() > 0)
				.filter(due -> text == null
						|| (due.partnerName() != null && due.partnerName().toLowerCase(java.util.Locale.ROOT).contains(text))
						|| due.publicId().toLowerCase(java.util.Locale.ROOT).contains(text)
						|| (!digits.isEmpty() && due.mobileNumber().replaceAll("\\D", "").contains(digits)))
				.toList();
		int from = (int) Math.min((long) pageRequest.getPageNumber() * pageRequest.getPageSize(), matching.size());
		int to = Math.min(from + pageRequest.getPageSize(), matching.size());
		Page<com.nexhome.app.dto.NexHomePartnerDueResponse> result = new org.springframework.data.domain.PageImpl<>(
				matching.subList(from, to), pageRequest, matching.size());
		return NexHomePaging.toPageResponse(pageRequest, result, result.getContent());
	}

	@Override
	@Transactional(readOnly = true)
	public com.nexhome.app.dto.NexHomeManualPayoutStatsResponse getManualPayoutStats() {
		java.util.List<com.nexhome.app.dto.NexHomePartnerDueResponse> dues = allDues();
		BigDecimal totalDue = dues.stream().map(com.nexhome.app.dto.NexHomePartnerDueResponse::balance)
				.filter(balance -> balance.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
		long partnersDue = dues.stream().filter(due -> due.balance().signum() > 0).count();
		Object[] all = manualPayoutRepository.paidSince(java.time.LocalDate.of(2000, 1, 1)).get(0);
		Object[] month = manualPayoutRepository.paidSince(java.time.LocalDate.now(ZONE).withDayOfMonth(1)).get(0);
		Object[] deductions = deductionRepository.totals().get(0);
		// Commission: online payouts' plus each collected cash job's (the service's commission on the cash)
		Object[] online = payoutRepository.commissionTotals().get(0);
		BigDecimal commission = (BigDecimal) online[0];
		long commissionJobs = ((Number) online[1]).longValue();
		for (com.nexhome.app.model.NexHomeCashCollection collection : cashCollectionRepository
				.findAllCollectedWithService()) {
			commission = commission.add(
					NexHomeBookingMapper.commissionOf(collection.getBooking().getService(), collection.getAmount()));
			commissionJobs++;
		}
		return new com.nexhome.app.dto.NexHomeManualPayoutStatsResponse(totalDue, partnersDue, (BigDecimal) all[1],
				((Number) all[0]).longValue(), (BigDecimal) month[1], ((Number) month[0]).longValue(),
				(BigDecimal) deductions[1], ((Number) deductions[0]).longValue(), commission, commissionJobs);
	}

	/** Recorded payouts as responses, with who recorded and cancelled them (one query for the names). */
	private java.util.List<com.nexhome.app.dto.NexHomeManualPayoutResponse> manualPayoutDtos(
			java.util.List<com.nexhome.app.model.NexHomePartnerManualPayout> payouts) {
		java.util.Set<Long> userIds = new java.util.HashSet<>();
		payouts.forEach(payout -> {
			if (payout.getCreatedBy() != null) userIds.add(payout.getCreatedBy());
			if (payout.getCancelledBy() != null) userIds.add(payout.getCancelledBy());
		});
		Map<Long, NexHomeAuditUsers.Person> people = userIds.isEmpty() ? Map.of() : auditUsers.people(userIds);
		// The deductions recorded with these payouts (one query)
		Map<Long, com.nexhome.app.dto.NexHomePartnerDeductionResponse> deductions = new java.util.HashMap<>();
		if (!payouts.isEmpty()) {
			deductionDtos(deductionRepository.findByPayoutIdIn(
					payouts.stream().map(com.nexhome.app.model.NexHomePartnerManualPayout::getId).toList()))
					.forEach(deduction -> deductions.put(deduction.payoutId(), deduction));
		}
		return payouts.stream().map(payout -> {
			NexHomePartnerProfile partner = payout.getPartner();
			String name = String.join(" ", java.util.stream.Stream.of(partner.getFirstName(), partner.getLastName())
					.filter(java.util.Objects::nonNull).toList()).trim();
			NexHomeAuditUsers.Person creator = people.get(payout.getCreatedBy());
			NexHomeAuditUsers.Person canceller = people.get(payout.getCancelledBy());
			return new com.nexhome.app.dto.NexHomeManualPayoutResponse(payout.getId(), payout.getPublicId(),
					partner.getId(), partner.getPublicId(), name.isEmpty() ? null : name, payout.getAmount(),
					payout.getPaymentMode(), payout.getTransactionId(), payout.getPaidOn(), payout.getNotes(),
					payout.getPayoutStatus(), payout.getCancelledAt(), canceller == null ? null : canceller.name(),
					payout.getCancellationReason(), payout.getCreatedBy(), creator == null ? null : creator.name(),
					payout.getCreationDate(), deductions.get(payout.getId()));
		}).toList();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Deductions (V63): what NexHome keeps from what it owes a partner (a penalty, damage, ...), never paid
	// ------------------------------------------------------------------------------------------------------------

	private String validDeductionReason(String code) {
		if (NexHomeText.trimToNull(code) == null) {
			throw new InvalidRequestException("Deduction reason is required.");
		}
		return lookupService.validateCode(Types.DEDUCTION_REASON, code, "Deduction reason");
	}

	/** The notes, required for OTHER. */
	private static String deductionNotes(String reason, String notes) {
		String trimmed = NexHomeText.trimToNull(notes);
		if (trimmed == null && "OTHER".equals(reason)) {
			throw new InvalidRequestException("Add a note saying what the deduction is for.");
		}
		return trimmed;
	}

	private com.nexhome.app.model.NexHomePartnerDeduction saveDeduction(NexHomePartnerProfile partner,
			com.nexhome.app.model.NexHomePartnerManualPayout payout, BigDecimal amount, String reason, String notes,
			java.time.LocalDate deductedOn, Long actingUserId) {
		com.nexhome.app.model.NexHomePartnerDeduction deduction = new com.nexhome.app.model.NexHomePartnerDeduction();
		deduction.setPartner(partner);
		deduction.setPayout(payout);
		deduction.setAmount(amount);
		deduction.setDeductionReason(reason);
		deduction.setNotes(notes);
		deduction.setDeductedOn(deductedOn);
		deduction.setCreatedBy(actingUserId);
		deduction.setUpdatedBy(actingUserId);
		deduction = deductionRepository.saveAndFlush(deduction);

		String reasonName = lookupService.getActiveLookupValues(Types.DEDUCTION_REASON).stream()
				.filter(value -> value.valueCode().equals(reason)).map(value -> value.displayName()).findFirst()
				.orElse(reason);
		notifier.notify(partner.getUser().getId(), new NexHomeNotifier.Message(NotificationType.PAYMENT,
				"NexHome deducted " + amount.toPlainString() + ": " + reasonName,
				notes == null ? "It was taken off what NexHome owes you." : notes,
				NexHomeNotifier.PARTNER_PROFILE, partner.getId()), actingUserId);
		auditRecorder.record("PARTNER_DEDUCTED", "PARTNER_DEDUCTION", deduction.getPublicId(), null,
				NexHomeAuditRecorder.values("partner", partner.getPublicId(), "amount", amount, "reason", reason,
						"notes", notes, "payout", payout == null ? null : payout.getPublicId(), "deductedOn",
						deductedOn.toString()),
				actingUserId);
		log.info("Deduction {} of {} from partner {} recorded", deduction.getPublicId(), amount,
				partner.getPublicId());
		return deduction;
	}

	@Override
	@Transactional
	public com.nexhome.app.dto.NexHomePartnerDeductionResponse recordDeduction(
			com.nexhome.app.dto.NexHomePartnerDeductionCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile partner = partnerProfileRepository.findById(request.partnerProfileId())
				.orElseThrow(() -> new ResourceNotFoundException("Partner profile does not exist."));
		String reason = validDeductionReason(request.deductionReason());
		String notes = deductionNotes(reason, request.notes());
		if (request.deductedOn().isAfter(java.time.LocalDate.now(ZONE))) {
			throw new InvalidRequestException("Deduction date cannot be in the future.");
		}
		BigDecimal balance = walletOf(partner).balance();
		if (request.amount().compareTo(balance) > 0) {
			throw new InvalidRequestException(balance.signum() > 0
					? "The deduction is more than NexHome owes the partner (" + balance.toPlainString() + ")."
					: "NexHome owes this partner nothing right now.");
		}
		return deductionDtos(java.util.List.of(saveDeduction(partner, null, request.amount(), reason, notes,
				request.deductedOn(), actingUserId))).get(0);
	}

	@Override
	@Transactional
	public com.nexhome.app.dto.NexHomePartnerDeductionResponse cancelDeduction(
			com.nexhome.app.dto.NexHomePartnerDeductionCancelRequest request) {
		Long actingUserId = permissionService.currentUserId();
		com.nexhome.app.model.NexHomePartnerDeduction deduction = deductionRepository.findById(request.deductionId())
				.orElseThrow(() -> new ResourceNotFoundException("Deduction does not exist."));
		if (com.nexhome.app.model.NexHomePartnerDeduction.CANCELLED.equals(deduction.getDeductionStatus())) {
			throw new InvalidRequestException("This deduction is already cancelled.");
		}
		deduction.setDeductionStatus(com.nexhome.app.model.NexHomePartnerDeduction.CANCELLED);
		deduction.setCancelledAt(Instant.now());
		deduction.setCancelledBy(actingUserId);
		deduction.setCancellationReason(request.reason().trim());
		deduction.setUpdatedBy(actingUserId);
		auditRecorder.record("PARTNER_DEDUCTION_CANCELLED", "PARTNER_DEDUCTION", deduction.getPublicId(),
				Map.of("status", com.nexhome.app.model.NexHomePartnerDeduction.RECORDED),
				Map.of("status", com.nexhome.app.model.NexHomePartnerDeduction.CANCELLED, "reason",
						request.reason().trim()),
				actingUserId);
		log.info("Deduction {} cancelled", deduction.getPublicId());
		return deductionDtos(java.util.List.of(deduction)).get(0);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<com.nexhome.app.dto.NexHomePartnerDeductionResponse> getDeductions(
			Long partnerProfileId, Integer page, Integer size) {
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.unsorted());
		Page<com.nexhome.app.model.NexHomePartnerDeduction> deductions;
		if (partnerProfileId == null) {
			if (!permissionService.can("PAYOUT_MANAGE", com.nexhome.app.model.NexHomeAccess.READ.name())) {
				throw new org.springframework.security.access.AccessDeniedException(
						"You do not have permission to see partner deductions.");
			}
			deductions = deductionRepository.findAllNewestFirst(pageRequest);
		}
		else {
			deductions = deductionRepository.findByPartnerNewestFirst(
					partnerForOwnerOrReader(partnerProfileId).getId(), pageRequest);
		}
		return NexHomePaging.toPageResponse(pageRequest, deductions, deductionDtos(deductions.getContent()));
	}

	/** Deductions as responses, with who recorded and cancelled them (one query for the names). */
	private java.util.List<com.nexhome.app.dto.NexHomePartnerDeductionResponse> deductionDtos(
			java.util.List<com.nexhome.app.model.NexHomePartnerDeduction> deductions) {
		java.util.Set<Long> userIds = new java.util.HashSet<>();
		deductions.forEach(deduction -> {
			if (deduction.getCreatedBy() != null) userIds.add(deduction.getCreatedBy());
			if (deduction.getCancelledBy() != null) userIds.add(deduction.getCancelledBy());
		});
		Map<Long, NexHomeAuditUsers.Person> people = userIds.isEmpty() ? Map.of() : auditUsers.people(userIds);
		return deductions.stream().map(deduction -> {
			NexHomePartnerProfile partner = deduction.getPartner();
			String name = String.join(" ", java.util.stream.Stream.of(partner.getFirstName(), partner.getLastName())
					.filter(java.util.Objects::nonNull).toList()).trim();
			NexHomeAuditUsers.Person creator = people.get(deduction.getCreatedBy());
			NexHomeAuditUsers.Person canceller = people.get(deduction.getCancelledBy());
			com.nexhome.app.model.NexHomePartnerManualPayout payout = deduction.getPayout();
			return new com.nexhome.app.dto.NexHomePartnerDeductionResponse(deduction.getId(), deduction.getPublicId(),
					partner.getId(), partner.getPublicId(), name.isEmpty() ? null : name,
					payout == null ? null : payout.getId(), payout == null ? null : payout.getPublicId(),
					deduction.getAmount(), deduction.getDeductionReason(), deduction.getNotes(),
					deduction.getDeductedOn(), deduction.getDeductionStatus(), deduction.getCancelledAt(),
					canceller == null ? null : canceller.name(), deduction.getCancellationReason(),
					deduction.getCreatedBy(), creator == null ? null : creator.name(), deduction.getCreationDate());
		}).toList();
	}

	private String optionalCode(String typeCode, String code, String fieldName) {
		String trimmed = NexHomeText.trimToNull(code);
		return trimmed == null ? null : lookupService.validateCode(typeCode, trimmed, fieldName);
	}

	private NexHomePartnerPayout findPayout(Long payoutId) {
		return payoutRepository.findById(payoutId)
				.orElseThrow(() -> new ResourceNotFoundException("Payout does not exist."));
	}
}
