package com.nexhome.app.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeBookingPaymentsResponse;
import com.nexhome.app.dto.NexHomeCashCollectionAcknowledgeRequest;
import com.nexhome.app.dto.NexHomeCashCollectionResponse;
import com.nexhome.app.dto.NexHomeCashCollectionUpdateRequest;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePaymentCaptureRequest;
import com.nexhome.app.dto.NexHomePaymentOrderCreateRequest;
import com.nexhome.app.dto.NexHomePaymentOrderResponse;
import com.nexhome.app.dto.NexHomePaymentResponse;
import com.nexhome.app.dto.NexHomePaymentStatsResponse;
import com.nexhome.app.dto.NexHomeRefundCreateRequest;
import com.nexhome.app.dto.NexHomeRefundResponse;
import com.nexhome.app.dto.NexHomeWebhookEventResponse;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.exception.UnauthorizedException;
import com.nexhome.app.mapper.NexHomePaymentMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeBooking;
import com.nexhome.app.model.NexHomeCashCollection;
import com.nexhome.app.model.NexHomeLookupCodes.BookingPaymentStatus;
import com.nexhome.app.model.NexHomeLookupCodes.CashCollectionStatus;
import com.nexhome.app.model.NexHomeLookupCodes.PaymentMethod;
import com.nexhome.app.model.NexHomeLookupCodes.PaymentOrderStatus;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeLookupCodes.WebhookProcessingStatus;
import com.nexhome.app.model.NexHomePayment;
import com.nexhome.app.model.NexHomePaymentOrder;
import com.nexhome.app.model.NexHomePaymentWebhookEvent;
import com.nexhome.app.repository.NexHomeBookingRepository;
import com.nexhome.app.repository.NexHomeCashCollectionRepository;
import com.nexhome.app.repository.NexHomePartnerPayoutRepository;
import com.nexhome.app.repository.NexHomePaymentOrderRepository;
import com.nexhome.app.repository.NexHomePaymentRefundRepository;
import com.nexhome.app.repository.NexHomePaymentRepository;
import com.nexhome.app.repository.NexHomePaymentWebhookEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomePaymentServiceImpl implements NexHomePaymentService {

	static final String PAYMENT_MANAGE = "PAYMENT_MANAGE";
	/** Webhooks act as the system account (user 1), which owns what the application does on its own. */
	private static final Long SYSTEM_USER_ID = 1L;
	private static final Duration ORDER_LIFETIME = Duration.ofDays(1);
	private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
	/** Months in the dashboard's revenue trend, this one included. */
	private static final int STATS_MONTHS = 10;
	private static final Set<String> OPEN_ORDERS = Set.of(PaymentOrderStatus.CREATED, PaymentOrderStatus.PENDING);
	/** Where a cash collection may go from each status. */
	private static final Map<String, Set<String>> CASH_TRANSITIONS = Map.of(
			CashCollectionStatus.DUE, Set.of(CashCollectionStatus.COLLECTED, CashCollectionStatus.DISPUTED,
					CashCollectionStatus.WAIVED),
			CashCollectionStatus.DISPUTED, Set.of(CashCollectionStatus.COLLECTED, CashCollectionStatus.WAIVED));

	private final NexHomePaymentOrderRepository orderRepository;
	private final NexHomePaymentRepository paymentRepository;
	private final NexHomePaymentRefundRepository refundRepository;
	private final NexHomePaymentWebhookEventRepository webhookRepository;
	private final NexHomeCashCollectionRepository cashCollectionRepository;
	private final NexHomePartnerPayoutRepository payoutRepository;
	private final NexHomeBookingRepository bookingRepository;
	private final NexHomeSettlement settlement;
	private final NexHomePaymentGateway gateway;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomePaymentMapper paymentMapper;
	private final JsonMapper jsonMapper;
	private final NexHomeAuditRecorder auditRecorder;
	private final NexHomeBookingCreator bookingCreator;

	// ------------------------------------------------------------------------------------------------------------
	// Orders and capture
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomePaymentOrderResponse createOrder(NexHomePaymentOrderCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeBooking booking = findBooking(request.bookingId());
		if (!isCustomer(booking) && !permissionService.can(PAYMENT_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Only the customer and staff can pay for a booking.");
		}
		if (!PaymentMethod.ONLINE.equals(booking.getPaymentMethod())) {
			throw new InvalidRequestException("Cash bookings are paid to the partner, not online.");
		}
		List<NexHomeBooking> unpaid = settlement.unpaidBookingsOf(booking.getCheckoutId());
		BigDecimal amount = unpaid.stream().map(NexHomeBooking::getTotalAmount).reduce(BigDecimal.ZERO,
				BigDecimal::add);
		if (amount.signum() <= 0) {
			throw new InvalidRequestException("Nothing is left to pay for this booking.");
		}
		List<NexHomePaymentOrder> orders = orderRepository.findByCheckoutIdOrderByIdDesc(booking.getCheckoutId());
		for (NexHomePaymentOrder open : orders) {
			if (OPEN_ORDERS.contains(open.getPaymentStatus())) {
				if (open.getAmount().compareTo(amount) == 0) {
					// Paying again for the same bookings reuses the open order
					return paymentMapper.toOrderDto(open);
				}
				// The bookings changed (one was cancelled): the old order goes
				open.setPaymentStatus(PaymentOrderStatus.CANCELLED);
				open.setUpdatedBy(actingUserId);
			}
		}

		String idempotencyKey = booking.getCheckoutId() + ":" + (orders.size() + 1);
		NexHomePaymentOrder order = new NexHomePaymentOrder();
		order.setCheckoutId(booking.getCheckoutId());
		order.setCustomer(booking.getCustomer());
		order.setProvider(gateway.provider());
		order.setProviderOrderId(gateway.createOrder(idempotencyKey, amount, booking.getCurrency()));
		order.setAmount(amount);
		order.setCurrency(booking.getCurrency());
		order.setPaymentStatus(PaymentOrderStatus.CREATED);
		order.setIdempotencyKey(idempotencyKey);
		order.setExpiresAt(Instant.now().plus(ORDER_LIFETIME));
		order.setCreatedBy(actingUserId);
		order.setUpdatedBy(actingUserId);
		order = orderRepository.save(order);
		log.info("Payment order {} of {} for checkout {}", order.getPublicId(), amount, booking.getCheckoutId());
		return paymentMapper.toOrderDto(order);
	}

	@Override
	@Transactional
	public NexHomeBookingPaymentsResponse capture(NexHomePaymentCaptureRequest request) {
		NexHomePaymentOrder order = orderRepository.findById(request.paymentOrderId())
				.orElseThrow(() -> new ResourceNotFoundException("Payment order does not exist."));
		List<NexHomePayment> payments = settlement.capture(order, NexHomeText.trimToNull(request.providerPaymentId()),
				permissionService.currentUserId());
		return bookingPayments(payments.get(0).getBooking(), true);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Webhooks
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional(noRollbackFor = UnauthorizedException.class)
	public NexHomeWebhookEventResponse receiveWebhook(String provider, String signature, String body) {
		if (!gateway.provider().equals(provider)) {
			throw new ResourceNotFoundException("Unknown payment provider.");
		}
		JsonNode event;
		try {
			event = jsonMapper.readTree(body);
		} catch (RuntimeException e) {
			throw new InvalidRequestException("The webhook body is not JSON.");
		}
		String eventId = text(event, "id");
		String eventType = text(event, "type");
		if (eventId == null || eventType == null) {
			throw new InvalidRequestException("The webhook needs an id and a type.");
		}
		// A repeated delivery is answered with what happened the first time
		var existing = webhookRepository.findByProviderAndProviderEventId(provider, eventId);
		if (existing.isPresent()) {
			return paymentMapper.toWebhookDto(existing.get());
		}

		NexHomePaymentWebhookEvent stored = new NexHomePaymentWebhookEvent();
		stored.setProvider(provider);
		stored.setProviderEventId(eventId);
		stored.setEventType(eventType);
		stored.setPayloadJson(body);
		stored.setSignatureVerified(gateway.verifySignature(body, signature));
		stored.setProcessingStatus(WebhookProcessingStatus.PENDING);
		stored.setReceivedAt(Instant.now());
		stored.setCreatedBy(SYSTEM_USER_ID);
		stored.setUpdatedBy(SYSTEM_USER_ID);
		stored = webhookRepository.save(stored);
		if (!stored.isSignatureVerified()) {
			// Kept for the record, never acted on
			stored.setProcessingStatus(WebhookProcessingStatus.IGNORED);
			stored.setProcessingError("The signature does not match.");
			stored.setProcessedAt(Instant.now());
			throw new UnauthorizedException("The webhook signature does not match.");
		}

		JsonNode data = event.path("data");
		try {
			NexHomePaymentOrder order = orderRepository.findByProviderAndProviderOrderId(provider, text(data, "orderId"))
					.orElse(null);
			switch (eventType) {
				case "payment.captured" -> {
					requireOrder(order);
					settlement.capture(order, text(data, "paymentId"), SYSTEM_USER_ID);
					stored.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
				}
				case "payment.failed" -> {
					requireOrder(order);
					settlement.fail(order, text(data, "errorCode"), text(data, "errorMessage"), SYSTEM_USER_ID);
					stored.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
				}
				default -> {
					stored.setProcessingStatus(WebhookProcessingStatus.IGNORED);
					stored.setProcessingError("Event type " + eventType + " is not handled.");
				}
			}
		} catch (InvalidRequestException | ResourceNotFoundException e) {
			stored.setProcessingStatus(WebhookProcessingStatus.FAILED);
			stored.setProcessingError(e.getMessage());
		}
		stored.setProcessedAt(Instant.now());
		log.info("Webhook {} {} from {}: {}", eventId, eventType, provider, stored.getProcessingStatus());
		return paymentMapper.toWebhookDto(stored);
	}

	private static void requireOrder(NexHomePaymentOrder order) {
		if (order == null) {
			throw new ResourceNotFoundException("No payment order has this provider order id.");
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() ? null : NexHomeText.trimToNull(value.asString());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Reading
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public NexHomeBookingPaymentsResponse getBookingPayments(Long bookingId) {
		NexHomeBooking booking = findBooking(bookingId);
		boolean staff = permissionService.can(PAYMENT_MANAGE, NexHomeAccess.READ.name())
				|| permissionService.can(NexHomeBookingServiceImpl.BOOKING_MANAGE, NexHomeAccess.READ.name());
		if (!staff && !isCustomer(booking)) {
			throw new AccessDeniedException("Not the booking's customer, and no " + PAYMENT_MANAGE + " READ access.");
		}
		return bookingPayments(booking, staff);
	}

	/** The booking's money; the partner's payout only for staff. */
	private NexHomeBookingPaymentsResponse bookingPayments(NexHomeBooking booking, boolean staff) {
		return new NexHomeBookingPaymentsResponse(
				orderRepository.findByCheckoutIdOrderByIdDesc(booking.getCheckoutId()).stream()
						.map(paymentMapper::toOrderDto).toList(),
				paymentRepository.findByBookingIdOrderByIdDesc(booking.getId()).stream()
						.map(this::toPaymentDto).toList(),
				refundRepository.findByBookingIdOrderByIdDesc(booking.getId()).stream()
						.map(paymentMapper::toRefundDto).toList(),
				cashCollectionRepository.findByBookingId(booking.getId()).map(paymentMapper::toCashDto).orElse(null),
				staff ? payoutRepository.findByBookingId(booking.getId()).map(paymentMapper::toPayoutDto).orElse(null)
						: null);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomePaymentResponse> getPayments(Integer page, Integer size, String status,
			Long customerProfileId, String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomePayment> payments = paymentRepository.findAll(NexHomePaymentRepository.paymentSpec(
				optionalCode(Types.PAYMENT_STATUS, status, "Payment status"), customerProfileId,
				NexHomePaging.searchText(search)), pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, payments, payments.stream().map(this::toPaymentDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomePaymentOrderResponse> getOrders(Integer page, Integer size) {
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.unsorted());
		Page<NexHomePaymentOrder> orders = orderRepository.findAllByOrderByCreationDateDescIdDesc(pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, orders, orders.stream().map(paymentMapper::toOrderDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePaymentStatsResponse getStats() {
		Object[] captured = paymentRepository.capturedTotals().get(0);
		Object[] refunded = refundRepository.succeededTotals().get(0);
		Object[] cash = cashCollectionRepository.dueTotals().get(0);

		YearMonth current = YearMonth.now(ZONE);
		Map<YearMonth, BigDecimal> revenue = new LinkedHashMap<>();
		for (NexHomePaymentRepository.MonthlyAmount row : paymentRepository.revenueByMonth()) {
			revenue.put(YearMonth.parse(row.getMonth()), row.getTotal());
		}
		YearMonth first = current.minusMonths(STATS_MONTHS - 1);
		// Months before the window count towards its start, months after it (clock differences) towards the total
		BigDecimal running = sumOf(revenue, month -> month.isBefore(first));
		List<NexHomePaymentStatsResponse.MonthRevenue> months = new ArrayList<>();
		for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
			BigDecimal inMonth = revenue.getOrDefault(month, BigDecimal.ZERO);
			running = running.add(inMonth);
			months.add(new NexHomePaymentStatsResponse.MonthRevenue(month.toString(), inMonth, running));
		}
		return new NexHomePaymentStatsResponse((BigDecimal) captured[0], ((Number) captured[1]).longValue(),
				(BigDecimal) refunded[0], ((Number) refunded[1]).longValue(),
				orderRepository.countByPaymentStatusIn(List.copyOf(OPEN_ORDERS)), (BigDecimal) cash[0],
				((Number) cash[1]).longValue(), running.add(sumOf(revenue, month -> month.isAfter(current))),
				revenue.getOrDefault(current, BigDecimal.ZERO), months);
	}

	private static BigDecimal sumOf(Map<YearMonth, BigDecimal> amounts, Predicate<YearMonth> which) {
		return amounts.entrySet().stream()
				.filter(entry -> which.test(entry.getKey()))
				.map(Map.Entry::getValue)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Refunds
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomeRefundResponse createRefund(NexHomeRefundCreateRequest request) {
		return paymentMapper.toRefundDto(settlement.refund(request.paymentId(), request.amount(),
				NexHomeText.trimToNull(request.reason()), permissionService.currentUserId()));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeRefundResponse> getRefunds(Integer page, Integer size, String status) {
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.unsorted());
		String code = optionalCode(Types.REFUND_STATUS, status, "Refund status");
		var refunds = code == null ? refundRepository.findAllByOrderByCreationDateDescIdDesc(pageRequest)
				: refundRepository.findByStatusOrderByCreationDateDescIdDesc(code, pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, refunds, refunds.stream().map(paymentMapper::toRefundDto).toList());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Cash
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeCashCollectionResponse> getCashCollections(Integer page, Integer size,
			String status) {
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.unsorted());
		String code = optionalCode(Types.CASH_COLLECTION_STATUS, status, "Cash collection status");
		var cash = code == null ? cashCollectionRepository.findAllByOrderByCreationDateDescIdDesc(pageRequest)
				: cashCollectionRepository.findByStatusOrderByCreationDateDescIdDesc(code, pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, cash, cash.stream().map(paymentMapper::toCashDto).toList());
	}

	@Override
	@Transactional
	public NexHomeCashCollectionResponse updateCashCollection(NexHomeCashCollectionUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeCashCollection cash = cashCollectionRepository.findById(request.cashCollectionId())
				.orElseThrow(() -> new ResourceNotFoundException("Cash collection does not exist."));
		String target = lookupService.validateCode(Types.CASH_COLLECTION_STATUS, request.status(), "Status");
		NexHomeBooking booking = cash.getBooking();
		boolean staff = permissionService.can(PAYMENT_MANAGE, NexHomeAccess.WRITE.name());
		boolean partner = booking.getPartner() != null
				&& booking.getPartner().getUser().getId().equals(actingUserId);
		// The partner only reports that they collected it; disputes and waivers are for staff
		if (!staff && !(partner && CashCollectionStatus.COLLECTED.equals(target))) {
			throw new AccessDeniedException("Only staff, or the partner reporting the cash collected, can change it.");
		}
		if (!CASH_TRANSITIONS.getOrDefault(cash.getStatus(), Set.of()).contains(target)) {
			throw new InvalidRequestException("Cash that is " + cash.getStatus() + " cannot be marked " + target + ".");
		}

		auditRecorder.record("CASH_" + target, "BOOKING", booking.getPublicId(), Map.of("cashStatus",
				cash.getStatus()), Map.of("cashStatus", target), actingUserId);
		cash.setStatus(target);
		if (request.notes() != null) {
			cash.setNotes(NexHomeText.trimToNull(request.notes()));
		}
		if (CashCollectionStatus.COLLECTED.equals(target)) {
			// The partner collected it, whoever records it
			cash.setCollectedBy(booking.getPartner() != null ? booking.getPartner().getUser().getId() : actingUserId);
			cash.setConfirmedAt(Instant.now());
			booking.setPaymentStatus(BookingPaymentStatus.PAID);
			booking.setUpdatedBy(actingUserId);
			// The booking's history shows the payment (same status: a payment event, V43)
			bookingCreator.recordStatus(booking, booking.getBookingStatus(), booking.getBookingStatus(),
					NexHomeSettlement.CASH_COLLECTED, actingUserId);
		}
		cash.setUpdatedBy(actingUserId);
		log.info("Cash for booking {} is now {}", booking.getPublicId(), target);
		return paymentMapper.toCashDto(cash);
	}

	@Override
	@Transactional
	public NexHomeCashCollectionResponse acknowledgeCashCollection(NexHomeCashCollectionAcknowledgeRequest request) {
		NexHomeCashCollection cash = cashCollectionRepository.findById(request.cashCollectionId())
				.orElseThrow(() -> new ResourceNotFoundException("Cash collection does not exist."));
		if (!isCustomer(cash.getBooking())) {
			throw new AccessDeniedException("Only the booking's customer acknowledges paying the cash.");
		}
		if (CashCollectionStatus.WAIVED.equals(cash.getStatus())) {
			throw new InvalidRequestException("The cash was waived; there is nothing to acknowledge.");
		}
		cash.setCustomerAcknowledgedAt(Instant.now());
		cash.setUpdatedBy(permissionService.currentUserId());
		return paymentMapper.toCashDto(cash);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeWebhookEventResponse> getWebhookEvents(Integer page, Integer size) {
		PageRequest pageRequest = NexHomePaging.of(page, size, Sort.unsorted());
		var events = webhookRepository.findAllByOrderByReceivedAtDescIdDesc(pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, events,
				events.stream().map(paymentMapper::toWebhookDto).toList());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------------------------------------------------

	private NexHomePaymentResponse toPaymentDto(NexHomePayment payment) {
		return paymentMapper.toPaymentDto(payment, refundRepository.sumOpenOrSucceeded(payment.getId()));
	}

	private boolean isCustomer(NexHomeBooking booking) {
		return booking.getCustomer().getUser().getId().equals(permissionService.currentUserId());
	}

	private String optionalCode(String typeCode, String code, String fieldName) {
		String trimmed = NexHomeText.trimToNull(code);
		return trimmed == null ? null : lookupService.validateCode(typeCode, trimmed, fieldName);
	}

	private NexHomeBooking findBooking(Long bookingId) {
		return bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking does not exist."));
	}
}
