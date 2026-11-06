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
import com.nexhome.app.dto.NexHomePayoutPayRequest;
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
import com.nexhome.app.model.NexHomeLookupCodes.TransferStatus;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeLookupCodes.VerificationStatus;
import com.nexhome.app.model.NexHomePartnerBankAccount;
import com.nexhome.app.model.NexHomePartnerPayout;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomePartnerTransfer;
import com.nexhome.app.repository.NexHomePartnerBankAccountRepository;
import com.nexhome.app.repository.NexHomePartnerPayoutRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerTransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomePayoutServiceImpl implements NexHomePayoutService {

	/** Where staff may move a payout (paying it is its own request). */
	private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
			PayoutStatus.PENDING, Set.of(PayoutStatus.ON_HOLD, PayoutStatus.CANCELLED),
			PayoutStatus.ON_HOLD, Set.of(PayoutStatus.PENDING, PayoutStatus.CANCELLED),
			PayoutStatus.FAILED, Set.of(PayoutStatus.PENDING, PayoutStatus.CANCELLED));
	/** Payouts that can be paid. */
	private static final Set<String> PAYABLE = Set.of(PayoutStatus.PENDING, PayoutStatus.FAILED);

	private final NexHomePartnerPayoutRepository payoutRepository;
	private final NexHomePartnerTransferRepository transferRepository;
	private final NexHomePartnerBankAccountRepository bankAccountRepository;
	private final NexHomePartnerProfileRepository partnerProfileRepository;
	private final NexHomePaymentGateway gateway;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomePaymentMapper paymentMapper;
	private final NexHomeBookingMapper bookingMapper;
	private final NexHomeNotifier notifier;
	private final NexHomeAuditRecorder auditRecorder;
	private final com.nexhome.app.repository.NexHomeBookingRepository bookingRepository;
	private final com.nexhome.app.repository.NexHomeCashCollectionRepository cashCollectionRepository;
	private final com.nexhome.app.repository.NexHomePartnerSettlementRepository settlementRepository;

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
	public NexHomePayoutResponse pay(NexHomePayoutPayRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerPayout payout = findPayout(request.payoutId());
		if (!PAYABLE.contains(payout.getPayoutStatus())) {
			throw new InvalidRequestException("A payout that is " + payout.getPayoutStatus() + " cannot be paid.");
		}
		if (payout.getNetAmount().signum() <= 0) {
			throw new InvalidRequestException("There is nothing to pay out; cancel the payout instead.");
		}
		NexHomePartnerBankAccount account = bankAccountRepository
				.findByPartnerIdAndPrimaryTrue(payout.getPartner().getId())
				.filter(found -> VerificationStatus.VERIFIED.equals(found.getVerificationStatus()))
				.orElseThrow(() -> new InvalidRequestException(
						"The partner needs a verified primary bank account before being paid."));

		// One idempotency key per attempt, kept for a retry of the same attempt
		int attempt = transferRepository.findByPayoutIdOrderByIdDesc(payout.getId()).size() + 1;
		NexHomePartnerTransfer transfer = new NexHomePartnerTransfer();
		transfer.setPayout(payout);
		transfer.setAmount(payout.getNetAmount());
		transfer.setStatus(TransferStatus.PROCESSING);
		transfer.setIdempotencyKey("payout:" + payout.getPublicId() + ":" + attempt);
		transfer.setCreatedBy(actingUserId);
		transfer.setUpdatedBy(actingUserId);
		transfer = transferRepository.save(transfer);
		payout.setPayoutStatus(PayoutStatus.PROCESSING);

		Map<String, Object> response = gateway.transfer(transfer.getIdempotencyKey(), payout.getNetAmount(),
				"XXXX" + account.getAccountNumberLast4() + " " + account.getIfscCode());
		boolean settled = "SETTLED".equals(response.get("status"));
		transfer.setProviderTransferId((String) response.get("transferId"));
		transfer.setProviderResponse(bookingMapper.toJson(response));
		transfer.setStatus(settled ? TransferStatus.SETTLED : TransferStatus.FAILED);
		transfer.setSettledAt(settled ? Instant.now() : null);
		payout.setPayoutStatus(settled ? PayoutStatus.PAID : PayoutStatus.FAILED);
		payout.setPaidAt(settled ? Instant.now() : null);
		payout.setProviderTransferId(transfer.getProviderTransferId());
		payout.setUpdatedBy(actingUserId);
		auditRecorder.record(settled ? "PAYOUT_PAID" : "PAYOUT_FAILED", "PAYOUT", payout.getPublicId(), null,
				Map.of("netAmount", payout.getNetAmount(), "transferStatus", transfer.getStatus()), actingUserId);
		if (!settled) {
			notifier.notifyStaff(NexHomeSettingsService.Keys.ALERT_PAYOUT_FAILED, "PAYOUT_MANAGE", new NexHomeNotifier.Message(NotificationType.PAYMENT,
					"Payout failed: " + payout.getPublicId(),
					"The transfer of " + payout.getNetAmount().toPlainString() + " to partner "
							+ payout.getPartner().getPublicId() + " failed; try again.",
					NexHomeNotifier.BOOKING, payout.getBooking().getId()), actingUserId);
		}
		if (settled) {
			notifier.notify(payout.getPartner().getUser().getId(), new NexHomeNotifier.Message(
					NotificationType.PAYMENT, "Payout " + payout.getPublicId() + " sent",
					"We paid you " + payout.getNetAmount().toPlainString() + " for booking "
							+ payout.getBooking().getPublicId() + ".",
					NexHomeNotifier.BOOKING, payout.getBooking().getId()), actingUserId);
		}
		log.info("Payout {} of {} to partner {}: {}", payout.getPublicId(), payout.getNetAmount(),
				payout.getPartner().getPublicId(), payout.getPayoutStatus());
		return paymentMapper.toPayoutDto(payout);
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
	// Wallet (V60): online payouts less the commission owed on collected cash, paid out in one transfer
	// ------------------------------------------------------------------------------------------------------------

	/** Online payouts a wallet payout settles (ON_HOLD ones wait; PAID, PROCESSING and CANCELLED are not owed). */
	private static final java.util.List<String> WALLET_PAYOUTS = java.util.List.of(PayoutStatus.PENDING,
			PayoutStatus.FAILED);

	/** What is in a wallet (or was in a wallet payout). */
	private record Wallet(java.util.List<NexHomePartnerPayout> payouts,
			java.util.List<com.nexhome.app.model.NexHomeCashCollection> cash,
			java.util.List<com.nexhome.app.dto.NexHomePartnerWalletResponse.Item> items, BigDecimal cashAmount,
			BigDecimal cashCommission, BigDecimal onlineAmount, BigDecimal onlineCommission, BigDecimal adjustments) {

		BigDecimal onlineEarnings() {
			return onlineAmount.subtract(onlineCommission).add(adjustments);
		}

		BigDecimal balance() {
			return onlineEarnings().subtract(cashCommission);
		}
	}

	private Wallet walletOf(java.util.List<NexHomePartnerPayout> payouts,
			java.util.List<com.nexhome.app.model.NexHomeCashCollection> cash) {
		java.util.List<com.nexhome.app.dto.NexHomePartnerWalletResponse.Item> items = new java.util.ArrayList<>();
		BigDecimal onlineAmount = BigDecimal.ZERO;
		BigDecimal onlineCommission = BigDecimal.ZERO;
		BigDecimal adjustments = BigDecimal.ZERO;
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
		for (com.nexhome.app.model.NexHomeCashCollection collection : cash) {
			com.nexhome.app.model.NexHomeBooking booking = collection.getBooking();
			BigDecimal commission = NexHomeBookingMapper.commissionOf(booking.getService(), collection.getAmount());
			cashAmount = cashAmount.add(collection.getAmount());
			cashCommission = cashCommission.add(commission);
			items.add(new com.nexhome.app.dto.NexHomePartnerWalletResponse.Item(booking.getId(),
					booking.getPublicId(), booking.getServiceNameSnapshot(), booking.getActualEndAt(),
					booking.getPaymentMethod(), collection.getAmount(), commission, BigDecimal.ZERO,
					commission.negate()));
		}
		// Latest job first
		items.sort(java.util.Comparator.comparing(com.nexhome.app.dto.NexHomePartnerWalletResponse.Item::completedAt,
				java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
		return new Wallet(payouts, cash, items, cashAmount, cashCommission, onlineAmount, onlineCommission,
				adjustments);
	}

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

	@Override
	@Transactional(readOnly = true)
	public com.nexhome.app.dto.NexHomePartnerWalletResponse getWallet(Long partnerProfileId) {
		NexHomePartnerProfile partner = partnerForOwnerOrReader(partnerProfileId);
		Wallet wallet = walletOf(payoutRepository.findUnsettled(partner.getId(), WALLET_PAYOUTS),
				cashCollectionRepository.findUnsettledByPartner(partner.getId()));
		java.util.List<NexHomePartnerPayout> onHold = payoutRepository.findUnsettled(partner.getId(),
				java.util.List.of(PayoutStatus.ON_HOLD));
		BigDecimal onHoldAmount = onHold.stream().map(NexHomePartnerPayout::getNetAmount).reduce(BigDecimal.ZERO,
				BigDecimal::add);
		var last = settlementRepository.findFirstByPartnerIdAndSettlementStatusOrderByPaidAtDescIdDesc(
				partner.getId(), com.nexhome.app.model.NexHomePartnerSettlement.PAID);
		BigDecimal paidTotal = settlementRepository.sumPaidOfPartner(partner.getId())
				.add(payoutRepository.sumPaidSeparately(partner.getId()));
		BigDecimal cashKept = cashCollectionRepository.findCollectedByPartnerWithService(partner.getId()).stream()
				.map(com.nexhome.app.model.NexHomeCashCollection::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new com.nexhome.app.dto.NexHomePartnerWalletResponse(partner.getId(),
				last.map(com.nexhome.app.model.NexHomePartnerSettlement::getPaidAt).orElse(null), wallet.cash().size(),
				wallet.cashAmount(), wallet.cashCommission(), wallet.payouts().size(), wallet.onlineAmount(),
				wallet.onlineCommission(), wallet.adjustments(), wallet.onlineEarnings(), wallet.balance(),
				onHold.size(), onHoldAmount, paidTotal, cashKept,
				last.map(found -> settlementDto(found, null)).orElse(null), wallet.items());
	}

	@Override
	@Transactional
	public com.nexhome.app.dto.NexHomePartnerSettlementResponse settleWallet(
			com.nexhome.app.dto.NexHomeWalletSettleRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile partner = partnerProfileRepository.findById(request.partnerProfileId())
				.orElseThrow(() -> new ResourceNotFoundException("Partner profile does not exist."));
		Wallet wallet = walletOf(payoutRepository.findUnsettled(partner.getId(), WALLET_PAYOUTS),
				cashCollectionRepository.findUnsettledByPartner(partner.getId()));
		if (wallet.items().isEmpty()) {
			throw new InvalidRequestException("The wallet is empty; there is nothing to pay out.");
		}
		BigDecimal net = wallet.balance();
		if (net.signum() <= 0) {
			throw new InvalidRequestException("The partner owes NexHome " + net.negate().toPlainString()
					+ " in commission on cash jobs; there is nothing to pay out.");
		}
		NexHomePartnerBankAccount account = bankAccountRepository.findByPartnerIdAndPrimaryTrue(partner.getId())
				.filter(found -> VerificationStatus.VERIFIED.equals(found.getVerificationStatus()))
				.orElseThrow(() -> new InvalidRequestException(
						"The partner needs a verified primary bank account before being paid."));

		long attempt = settlementRepository.countByPartnerId(partner.getId()) + 1;
		Map<String, Object> response = gateway.transfer("settlement:" + partner.getPublicId() + ":" + attempt, net,
				"XXXX" + account.getAccountNumberLast4() + " " + account.getIfscCode());
		boolean settled = "SETTLED".equals(response.get("status"));
		Instant now = Instant.now();

		com.nexhome.app.model.NexHomePartnerSettlement settlement = new com.nexhome.app.model.NexHomePartnerSettlement();
		settlement.setPartner(partner);
		settlement.setBankAccount(account);
		settlement.setBankAccountLast4(account.getAccountNumberLast4());
		settlement.setBankName(account.getBankName());
		settlement.setCashJobs(wallet.cash().size());
		settlement.setCashAmount(wallet.cashAmount());
		settlement.setCashCommission(wallet.cashCommission());
		settlement.setOnlineJobs(wallet.payouts().size());
		settlement.setOnlineAmount(wallet.onlineAmount());
		settlement.setOnlineCommission(wallet.onlineCommission());
		settlement.setAdjustmentsAmount(wallet.adjustments());
		settlement.setNetAmount(net);
		settlement.setSettlementStatus(settled ? com.nexhome.app.model.NexHomePartnerSettlement.PAID
				: com.nexhome.app.model.NexHomePartnerSettlement.FAILED);
		settlement.setProviderTransferId((String) response.get("transferId"));
		settlement.setProviderResponse(bookingMapper.toJson(response));
		settlement.setPaidAt(settled ? now : null);
		settlement.setCreatedBy(actingUserId);
		settlement.setUpdatedBy(actingUserId);
		settlement = settlementRepository.save(settlement);

		if (settled) {
			// Everything in the wallet is settled by it: the wallet starts again at 0
			for (NexHomePartnerPayout payout : wallet.payouts()) {
				payout.setSettlement(settlement);
				payout.setPayoutStatus(PayoutStatus.PAID);
				payout.setPaidAt(now);
				payout.setProviderTransferId(settlement.getProviderTransferId());
				payout.setUpdatedBy(actingUserId);
			}
			for (com.nexhome.app.model.NexHomeCashCollection collection : wallet.cash()) {
				collection.setSettlement(settlement);
				collection.setUpdatedBy(actingUserId);
			}
			notifier.notify(partner.getUser().getId(), new NexHomeNotifier.Message(NotificationType.PAYMENT,
					"NexHome paid you " + net.toPlainString(),
					"Your wallet for " + wallet.items().size() + " jobs was paid to your bank account ending "
							+ account.getAccountNumberLast4() + ".",
					NexHomeNotifier.PARTNER_PROFILE, partner.getId()), actingUserId);
		}
		else {
			notifier.notifyStaff(NexHomeSettingsService.Keys.ALERT_PAYOUT_FAILED, "PAYOUT_MANAGE",
					new NexHomeNotifier.Message(NotificationType.PAYMENT, "Payout failed: " + partner.getPublicId(),
							"The wallet payout of " + net.toPlainString() + " to partner " + partner.getPublicId()
									+ " failed; try again.",
							NexHomeNotifier.PARTNER_PROFILE, partner.getId()),
					actingUserId);
		}
		auditRecorder.record(settled ? "WALLET_PAID" : "WALLET_PAYOUT_FAILED", "PARTNER_SETTLEMENT",
				settlement.getPublicId(), null,
				Map.of("partner", partner.getPublicId(), "netAmount", net, "cashCommission", wallet.cashCommission(),
						"onlineEarnings", wallet.onlineEarnings(), "jobs", wallet.items().size()),
				actingUserId);
		log.info("Wallet payout {} of {} to partner {}: {}", settlement.getPublicId(), net, partner.getPublicId(),
				settlement.getSettlementStatus());
		return settlementDto(settlement, wallet.items());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<com.nexhome.app.dto.NexHomePartnerSettlementResponse> getSettlements(
			Long partnerProfileId, Integer page, Integer size) {
		NexHomePartnerProfile partner = partnerForOwnerOrReader(partnerProfileId);
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.unsorted());
		var settlements = settlementRepository.findByPartnerIdOrderByCreationDateDescIdDesc(partner.getId(),
				pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, settlements,
				settlements.stream().map(found -> settlementDto(found, null)).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public com.nexhome.app.dto.NexHomePartnerSettlementResponse getSettlement(Long settlementId) {
		com.nexhome.app.model.NexHomePartnerSettlement settlement = settlementRepository.findById(settlementId)
				.orElseThrow(() -> new ResourceNotFoundException("Payout does not exist."));
		requireOwnerOrReader(settlement.getPartner());
		Wallet jobs = walletOf(payoutRepository.findBySettlement(settlement.getId()),
				cashCollectionRepository.findBySettlement(settlement.getId()));
		return settlementDto(settlement, jobs.items());
	}

	private com.nexhome.app.dto.NexHomePartnerSettlementResponse settlementDto(
			com.nexhome.app.model.NexHomePartnerSettlement settlement,
			java.util.List<com.nexhome.app.dto.NexHomePartnerWalletResponse.Item> items) {
		return new com.nexhome.app.dto.NexHomePartnerSettlementResponse(settlement.getId(), settlement.getPublicId(),
				settlement.getPartner().getId(), settlement.getPartner().getPublicId(), settlement.getCashJobs(),
				settlement.getCashAmount(), settlement.getCashCommission(), settlement.getOnlineJobs(),
				settlement.getOnlineAmount(), settlement.getOnlineCommission(), settlement.getAdjustmentsAmount(),
				settlement.getOnlineAmount().subtract(settlement.getOnlineCommission())
						.add(settlement.getAdjustmentsAmount()),
				settlement.getNetAmount(), settlement.getSettlementStatus(), settlement.getProviderTransferId(),
				settlement.getBankName(), settlement.getBankAccountLast4(), settlement.getPaidAt(),
				settlement.getCreationDate(), items);
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
