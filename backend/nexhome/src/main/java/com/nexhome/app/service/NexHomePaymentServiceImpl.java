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
	private final com.nexhome.app.repository.NexHomePaymentAttemptRepository attemptRepository;
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
	private final NexHomeNotifier notifier;

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
				// The same bookings through the current gateway (an order of another provider is not reused)
				if (open.getAmount().compareTo(amount) == 0 && open.getProvider().equals(gateway.provider())) {
					// Paying again for the same bookings reuses the open order
					return paymentMapper.toOrderDto(open);
				}
				// The bookings changed (one was cancelled): the old order goes
				open.setPaymentStatus(PaymentOrderStatus.CANCELLED);
				open.setUpdatedBy(actingUserId);
			}
		}

		String idempotencyKey = booking.getCheckoutId() + ":" + (orders.size() + 1);
		// The gateway's order id: letters, digits and _ only (Cashfree: at most 45, unique for the merchant for good).
		// The count alone could repeat an id the gateway already has (an order removed from the database, a database
		// reset), which the gateway refuses, so the time (seconds, base 36) follows it: NH<checkout>_<n>x<time>, ≤ 44
		String reference = "NH" + booking.getCheckoutId().toString().replace("-", "") + "_" + (orders.size() + 1) + "x"
				+ Long.toString(Instant.now().getEpochSecond(), 36);
		com.nexhome.app.model.NexHomeCustomerProfile customer = booking.getCustomer();
		String phone = customer.getUser().getMobileNumber();
		NexHomePaymentGateway.Order created = gateway.createOrder(reference, amount, booking.getCurrency(),
				new NexHomePaymentGateway.Customer(customer.getPublicId(),
						phone.length() > 10 ? phone.substring(phone.length() - 10) : phone,
						com.nexhome.app.mapper.NexHomeBookingMapper.nameOf(customer.getFirstName(), customer.getLastName()),
						customer.getUser().getEmail()));
		NexHomePaymentOrder order = new NexHomePaymentOrder();
		order.setCheckoutId(booking.getCheckoutId());
		order.setCustomer(booking.getCustomer());
		order.setProvider(gateway.provider());
		order.setProviderOrderId(created.providerOrderId());
		order.setCheckoutToken(created.checkoutToken());
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

	/**
	 * Asks the gateway whether an order was paid (the app after its checkout closes; staff checking): PAID captures it
	 * (as a webhook would), EXPIRED ends it, anything else leaves it open. The customer of the order or
	 * PAYMENT_MANAGE write.
	 */
	@Override
	@Transactional
	public NexHomePaymentOrderResponse verifyOrder(Long paymentOrderId) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePaymentOrder order = orderRepository.findById(paymentOrderId)
				.orElseThrow(() -> new ResourceNotFoundException("Payment order does not exist."));
		boolean owner = order.getCustomer().getUser().getId().equals(actingUserId);
		if (!owner && !permissionService.can(PAYMENT_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Only the customer and staff can check this payment.");
		}
		if (!OPEN_ORDERS.contains(order.getPaymentStatus())) {
			// Already settled: a paid order gives the gateway's reference of its payment
			String reference = PaymentOrderStatus.PAID.equals(order.getPaymentStatus())
					? paymentRepository.findByPaymentOrderIdOrderByIdAsc(order.getId()).stream()
							.map(NexHomePayment::getProviderPaymentId).filter(java.util.Objects::nonNull).findFirst()
							.orElse(null)
					: null;
			return paymentMapper.toOrderDto(order).withAttempt(reference == null ? null : "SUCCESS", null, reference);
		}
		if (!order.getProvider().equals(gateway.provider())) {
			throw new InvalidRequestException("This payment order was not made through " + gateway.provider() + ".");
		}
		NexHomePaymentGateway.OrderStatus status = gateway.fetchOrder(order.getProviderOrderId());
		if (status == null) {
			throw new InvalidRequestException("Online payments are not set up yet; staff record this payment.");
		}
		// Every attempt kept (V67); new failed / pending / cancelled ones go into the bookings' history
		settlement.recordAttempts(order, status.attempts(), actingUserId);
		switch (status.status()) {
			case NexHomePaymentGateway.OrderStatus.PAID ->
				settlement.capture(order, status.providerPaymentId(), status.paymentMode(), actingUserId);
			case NexHomePaymentGateway.OrderStatus.EXPIRED -> {
				order.setPaymentStatus(PaymentOrderStatus.EXPIRED);
				order.setUpdatedBy(actingUserId);
			}
			default -> {
				// Not paid yet: the order stays open, the customer may pay or try again
			}
		}
		log.info("Payment order {} checked with {}: {} (last attempt {})", order.getPublicId(), gateway.provider(),
				status.status(), status.attemptStatus());
		return paymentMapper.toOrderDto(order).withAttempt(status.attemptStatus(), status.failureMessage(),
				status.attemptId());
	}

	/**
	 * The hosted payment page the app opens in the phone's browser (public): it loads the gateway's checkout with the
	 * order's token and, when the customer is done or closes it, returns to the app ({@code returnUrl}: only the app's
	 * own nexhome:// address, or Expo Go's exp:// one while developing). The app then checks the order.
	 */
	@Override
	public String checkoutPage(String checkoutToken, String returnUrl) {
		String mode = gateway.checkoutMode();
		if (mode == null) {
			throw new InvalidRequestException("Online payments are not set up yet.");
		}
		if (checkoutToken == null || !checkoutToken.matches("[A-Za-z0-9_.=-]{10,1000}")) {
			throw new InvalidRequestException("This payment link is not valid.");
		}
		if (returnUrl == null || !(returnUrl.startsWith("nexhome://") || returnUrl.startsWith("exp://")
				|| returnUrl.startsWith("exps://")) || returnUrl.length() > 500) {
			throw new InvalidRequestException("This payment link is not valid.");
		}
		// Values go into the script as JSON strings, with "</" broken up so they cannot close the script
		String session = jsonMapper.writeValueAsString(checkoutToken).replace("</", "<\\/");
		String back = jsonMapper.writeValueAsString(returnUrl).replace("</", "<\\/");
		return """
				<!doctype html>
				<html lang="en">
				<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width, initial-scale=1">
				<title>NexHome payment</title>
				<style>
				  body { margin: 0; font-family: system-ui, sans-serif; background: #f4f8fc; color: #0b2545;
				         display: flex; min-height: 100vh; align-items: center; justify-content: center; }
				  .box { text-align: center; padding: 24px; }
				  .brand { font-weight: 800; font-size: 22px; color: #0b438b; }
				  .brand span { color: #168f5b; }
				  p { color: #5f7084; }
				  a { display: inline-block; margin-top: 12px; padding: 12px 20px; border-radius: 12px; background: #0b438b;
				      color: #fff; text-decoration: none; font-weight: 700; }
				  /* Cashfree's checkout fills the whole screen, edge to edge (its modal is a centred box with gaps) */
				  #checkout { position: fixed; top: 0; left: 0; right: 0; bottom: 0; z-index: 10; background: #fff;
				              display: none; }
				  #checkout.open { display: block; }
				  #checkout > div, #checkout > div > div, #checkout iframe { margin: 0 !important; padding: 0 !important;
				              border: 0 !important; border-radius: 0 !important; box-shadow: none !important;
				              max-width: none !important; max-height: none !important; transform: none !important;
				              top: 0 !important; left: 0 !important; width: 100%% !important; height: 100%% !important; }
				</style>
				<script src="https://sdk.cashfree.com/js/v3/cashfree.js"></script>
				</head>
				<body>
				<div class="box">
				  <div class="brand">Nex<span>Home</span></div>
				  <p id="note">Opening secure payment…</p>
				  <a id="back" href="#" style="display:none">Back to NexHome</a>
				</div>
				<div id="checkout"></div>
				<script>
				  var session = %s, back = %s, mode = "%s";
				  function done(status) {
				    location.href = back + (back.indexOf("?") >= 0 ? "&" : "?") + "status=" + status;
				  }
				  document.getElementById("back").onclick = function (e) { e.preventDefault(); done("closed"); };
				  var frame = document.getElementById("checkout");
				  function failed() {
				    frame.className = "";
				    document.getElementById("note").textContent = "The payment page could not open. Go back and try again.";
				    document.getElementById("back").style.display = "inline-block";
				  }
				  try {
				    frame.className = "open";
				    // Inline in a full-screen box rather than "_modal", so no step of the checkout shows side gaps
				    Cashfree({ mode: mode }).checkout({ paymentSessionId: session, redirectTarget: frame,
				        appearance: { width: "100%%", height: "100%%", borderRadius: "0px", borderWidth: "0px" } })
				      .then(function (result) {
				        if (result && result.paymentDetails) { done("done"); }
				        else if (result && result.error) { done("closed"); }
				      }, failed);
				  } catch (e) {
				    failed();
				  }
				  setTimeout(function () { document.getElementById("back").style.display = "inline-block"; }, 8000);
				</script>
				</body>
				</html>
				""".formatted(session, back, mode);
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
	public NexHomeWebhookEventResponse receiveWebhook(String provider, Map<String, String> headers, String body) {
		if (!gateway.provider().equalsIgnoreCase(provider)) {
			throw new ResourceNotFoundException("Unknown payment provider.");
		}
		JsonNode event;
		try {
			event = jsonMapper.readTree(body);
		} catch (RuntimeException e) {
			throw new InvalidRequestException("The webhook body is not JSON.");
		}
		provider = gateway.provider();
		NexHomePaymentGateway.WebhookEvent parsed = gateway.parseWebhook(event);
		String eventId = parsed.eventId();
		String eventType = parsed.type();
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
		stored.setSignatureVerified(gateway.verifyWebhook(body, headers));
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

		try {
			NexHomePaymentOrder order = parsed.providerOrderId() == null ? null
					: orderRepository.findByProviderAndProviderOrderId(provider, parsed.providerOrderId()).orElse(null);
			switch (eventType) {
				case "payment.captured" -> {
					requireOrder(order);
					// The payment as an attempt first (V67), with any surcharge the customer paid (V69)
					if (parsed.attempt() != null) {
						settlement.recordAttempts(order, List.of(parsed.attempt()), SYSTEM_USER_ID);
					}
					if (PaymentOrderStatus.CANCELLED.equals(order.getPaymentStatus())) {
						// Paid after the order was cancelled (cash collected or booking cancelled): staff refund it
						settlement.lateOnlinePayment(order, parsed.providerPaymentId(), SYSTEM_USER_ID);
						stored.setProcessingStatus(WebhookProcessingStatus.FAILED);
						stored.setProcessingError("Paid after the order was cancelled; refund it at the gateway.");
						break;
					}
					settlement.capture(order, parsed.providerPaymentId(), parsed.paymentMode(), SYSTEM_USER_ID);
					stored.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
				}
				case "payment.failed" -> {
					requireOrder(order);
					// The attempt first (V67): its history line and the customer's "Payment failed", once per attempt
					if (parsed.attempt() != null) {
						settlement.recordAttempts(order, List.of(parsed.attempt()), SYSTEM_USER_ID);
					}
					settlement.fail(order, parsed.errorCode(), parsed.errorMessage(), SYSTEM_USER_ID);
					stored.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
				}
				case "payment.charges" -> {
					requireOrder(order);
					if (settlement.recordCharges(order, parsed.charges(), SYSTEM_USER_ID)) {
						stored.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
					}
					else {
						stored.setProcessingStatus(WebhookProcessingStatus.IGNORED);
						stored.setProcessingError("The order has no successful payment to put the charges on.");
					}
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
			// The assigned partner sees only the cash collection (to mark the cash collected), not the payments
			if (isAssignedPartner(booking)) {
				return new NexHomeBookingPaymentsResponse(List.of(), List.of(), List.of(),
						cashCollectionRepository.findByBookingId(booking.getId()).map(paymentMapper::toCashDto)
								.orElse(null),
						null);
			}
			throw new AccessDeniedException("Not the booking's customer, and no " + PAYMENT_MANAGE + " READ access.");
		}
		return bookingPayments(booking, staff);
	}

	private boolean isAssignedPartner(NexHomeBooking booking) {
		return booking.getPartner() != null
				&& booking.getPartner().getUser().getId().equals(permissionService.currentUserId());
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
	public com.nexhome.app.dto.NexHomePaymentOrderDetailResponse getOrder(Long paymentOrderId) {
		NexHomePaymentOrder order = orderRepository.findById(paymentOrderId)
				.orElseThrow(() -> new ResourceNotFoundException("Payment order does not exist."));
		List<NexHomePaymentOrderResponse.OrderBooking> bookings = bookingRepository
				.findByCheckoutIdInAndPaymentMethodOrderByIdAsc(List.of(order.getCheckoutId()),
						com.nexhome.app.model.NexHomeLookupCodes.PaymentMethod.ONLINE)
				.stream().map(booking -> new NexHomePaymentOrderResponse.OrderBooking(booking.getId(),
						booking.getPublicId()))
				.toList();
		return new com.nexhome.app.dto.NexHomePaymentOrderDetailResponse(
				paymentMapper.toOrderDto(order).withBookings(bookings),
				attemptRepository.findByPaymentOrderIdOrderByAttemptedAtDescIdDesc(order.getId()).stream()
						.map(paymentMapper::toAttemptDto).toList(),
				paymentRepository.findByPaymentOrderIdOrderByIdAsc(order.getId()).stream().map(this::toPaymentDto)
						.toList());
	}

	@Override
	@Transactional
	public com.nexhome.app.dto.NexHomePaymentOrderDetailResponse syncOrder(Long paymentOrderId) {
		NexHomePaymentOrder order = orderRepository.findById(paymentOrderId)
				.orElseThrow(() -> new ResourceNotFoundException("Payment order does not exist."));
		if (NexHomeManualPaymentGateway.PROVIDER.equals(order.getProvider())) {
			throw new InvalidRequestException("This order was recorded by staff; there is nothing to fetch.");
		}
		if (!order.getProvider().equals(gateway.provider())) {
			throw new InvalidRequestException("This payment order was not made through " + gateway.provider() + ".");
		}
		if (OPEN_ORDERS.contains(order.getPaymentStatus())) {
			verifyOrder(paymentOrderId);
		}
		else {
			NexHomePaymentGateway.OrderStatus status = gateway.fetchOrder(order.getProviderOrderId());
			if (status != null) {
				settlement.recordAttempts(order, status.attempts(), permissionService.currentUserId());
			}
		}
		// The gateway's charges and settlement, once it has them (V68)
		if (com.nexhome.app.model.NexHomeLookupCodes.PaymentOrderStatus.PAID.equals(order.getPaymentStatus())) {
			settlement.recordCharges(order, gateway.fetchCharges(order.getProviderOrderId()),
					permissionService.currentUserId());
		}
		return getOrder(paymentOrderId);
	}

	// ------------------------------------------------------------------------------------------------------------
	// An online booking's payment, from the partner's side
	// ------------------------------------------------------------------------------------------------------------

	/** Gateway orders that may still be paid (a failed attempt leaves the gateway's order payable). */
	private static final Set<String> PAYABLE_ORDERS = Set.of(PaymentOrderStatus.CREATED, PaymentOrderStatus.PENDING,
			PaymentOrderStatus.AUTHORIZED, PaymentOrderStatus.FAILED);

	/** History reason when an online booking is paid in cash instead. */
	static final String PAID_IN_CASH_INSTEAD = "Payment mode changed from online to cash";

	@Override
	@Transactional
	public com.nexhome.app.dto.NexHomeBookingPaymentStatusResponse checkBookingPayment(Long bookingId) {
		NexHomeBooking booking = findBooking(bookingId);
		if (!isCustomer(booking) && !isAssignedPartner(booking)
				&& !permissionService.can(PAYMENT_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Only the booking's customer, its partner and staff can check its payment.");
		}
		NexHomePaymentGateway.OrderStatus last = awaitingOnline(booking)
				? checkOpenOrders(booking, permissionService.currentUserId())
				: null;
		return paymentStatusOf(booking, last);
	}

	@Override
	@Transactional
	public com.nexhome.app.dto.NexHomeBookingPaymentStatusResponse collectCashInstead(
			com.nexhome.app.dto.NexHomeCollectCashRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeBooking booking = findBooking(request.bookingId());
		if (!isAssignedPartner(booking) && !permissionService.can(PAYMENT_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Only the booking's partner and staff can record cash collected for it.");
		}
		if (!PaymentMethod.ONLINE.equals(booking.getPaymentMethod())) {
			throw new InvalidRequestException("This booking is paid in cash already; mark its cash collected instead.");
		}
		if (com.nexhome.app.model.NexHomeLookupCodes.BookingStatus.CANCELLED.equals(booking.getBookingStatus())) {
			throw new InvalidRequestException("A cancelled booking has nothing to collect.");
		}
		// Once the work has started: the partner's app stops the work after the cash (as for a cash job)
		if (!Set.of(com.nexhome.app.model.NexHomeLookupCodes.BookingStatus.IN_PROGRESS,
				com.nexhome.app.model.NexHomeLookupCodes.BookingStatus.PAUSED,
				com.nexhome.app.model.NexHomeLookupCodes.BookingStatus.COMPLETED).contains(booking.getBookingStatus())) {
			throw new InvalidRequestException("Start the work first; the customer pays once it is done.");
		}
		// A payment that came in meanwhile wins: nothing changes, the booking is paid online
		NexHomePaymentGateway.OrderStatus last = awaitingOnline(booking) ? checkOpenOrders(booking, actingUserId)
				: null;
		if (!awaitingOnline(booking)) {
			return paymentStatusOf(booking, last);
		}

		// The open online payments end, at the gateway too, so the customer cannot pay twice. A gateway that cannot
		// end an order (Cashfree refuses while a payment of it is still pending at the bank, or cannot be reached) does
		// not stop the cash: the order is cancelled here, and a payment that still succeeds on it later is not
		// captured but reported to the payment staff to refund (`lateOnlinePayment`).
		for (NexHomePaymentOrder order : orderRepository.findByCheckoutIdOrderByIdDesc(booking.getCheckoutId())) {
			if (!PAYABLE_ORDERS.contains(order.getPaymentStatus())) {
				continue;
			}
			boolean endedAtGateway = true;
			if (!NexHomeManualPaymentGateway.PROVIDER.equals(order.getProvider())
					&& order.getProvider().equals(gateway.provider()) && order.getProviderOrderId() != null) {
				try {
					gateway.cancelOrder(order.getProviderOrderId());
				}
				catch (RuntimeException e) {
					endedAtGateway = false;
					log.warn("Payment order {} could not be ended at the gateway; cancelled here only: {}",
							order.getPublicId(), e.getMessage());
				}
			}
			auditRecorder.record("PAYMENT_ORDER_CANCELLED", "PAYMENT_ORDER", order.getPublicId(),
					Map.of("paymentStatus", order.getPaymentStatus()),
					Map.of("paymentStatus", PaymentOrderStatus.CANCELLED, "reason", "Paid in cash",
							"endedAtGateway", endedAtGateway),
					actingUserId);
			order.setPaymentStatus(PaymentOrderStatus.CANCELLED);
			order.setUpdatedBy(actingUserId);
		}

		// The booking becomes a cash booking; an online payment fee (bookings before V68) is dropped with it
		String status = booking.getBookingStatus();
		BigDecimal fee = booking.getTransactionFeeAmount() == null ? BigDecimal.ZERO : booking.getTransactionFeeAmount();
		if (fee.signum() > 0) {
			booking.setTotalAmount(booking.getTotalAmount().subtract(fee));
			booking.setTransactionFeeAmount(BigDecimal.ZERO);
		}
		booking.setPaymentMethod(PaymentMethod.CASH);
		booking.setPaymentStatus(BookingPaymentStatus.PAID);
		booking.setUpdatedBy(actingUserId);
		String notes = NexHomeText.trimToNull(request.notes());
		bookingCreator.recordStatus(booking, status, status, PAID_IN_CASH_INSTEAD
				+ (last != null && last.failureMessage() != null ? " · " + last.failureMessage() : ""), actingUserId);

		NexHomeCashCollection cash = cashCollectionRepository.findByBookingId(booking.getId()).orElseGet(() -> {
			NexHomeCashCollection created = new NexHomeCashCollection();
			created.setBooking(booking);
			created.setCreatedBy(actingUserId);
			return created;
		});
		cash.setAmount(booking.getTotalAmount());
		cash.setStatus(CashCollectionStatus.COLLECTED);
		cash.setCollectedBy(booking.getPartner() != null ? booking.getPartner().getUser().getId() : actingUserId);
		cash.setConfirmedAt(Instant.now());
		cash.setNotes(notes != null ? notes : "Online payment did not go through; paid in cash");
		cash.setUpdatedBy(actingUserId);
		if (cash.getId() == null) {
			cashCollectionRepository.save(cash);
		}
		bookingCreator.recordStatus(booking, booking.getBookingStatus(), booking.getBookingStatus(),
				NexHomeSettlement.CASH_COLLECTED, actingUserId);
		// A job waiting IN_PROGRESS for its payment is completed by the cash
		settlement.completeAfterCash(booking, actingUserId);

		auditRecorder.record("PAYMENT_MODE_CHANGED", "BOOKING", booking.getPublicId(),
				Map.of("paymentMethod", PaymentMethod.ONLINE),
				NexHomeAuditRecorder.values("paymentMethod", PaymentMethod.CASH, "cashCollected", cash.getAmount(),
						"notes", notes),
				actingUserId);
		notifier.notify(booking.getCustomer().getUser().getId(), new NexHomeNotifier.Message(
				com.nexhome.app.model.NexHomeLookupCodes.NotificationType.PAYMENT,
				"Paid in cash for " + booking.getPublicId(),
				"Your professional collected " + cash.getAmount().toPlainString() + " " + booking.getCurrency()
						+ " in cash for " + booking.getServiceNameSnapshot()
						+ ". The online payment is cancelled; you will not be charged online. Open the booking to"
						+ " confirm you paid.",
				NexHomeNotifier.BOOKING, booking.getId()), actingUserId);
		log.info("Booking {} paid in cash instead of online ({})", booking.getPublicId(), cash.getAmount());
		return paymentStatusOf(booking, last);
	}

	/** An online booking whose payment has not come in. */
	private static boolean awaitingOnline(NexHomeBooking booking) {
		return PaymentMethod.ONLINE.equals(booking.getPaymentMethod())
				&& !com.nexhome.app.model.NexHomeLookupCodes.BookingStatus.CANCELLED.equals(booking.getBookingStatus())
				&& Set.of(BookingPaymentStatus.PENDING, BookingPaymentStatus.FAILED).contains(booking.getPaymentStatus());
	}

	/**
	 * Asks the gateway about the open orders of the booking's checkout: a paid one is captured, an expired one ends.
	 * Returns the latest answer (null: none was asked).
	 */
	private NexHomePaymentGateway.OrderStatus checkOpenOrders(NexHomeBooking booking, Long actingUserId) {
		NexHomePaymentGateway.OrderStatus last = null;
		for (NexHomePaymentOrder order : orderRepository.findByCheckoutIdOrderByIdDesc(booking.getCheckoutId())) {
			if (!PAYABLE_ORDERS.contains(order.getPaymentStatus()) || !order.getProvider().equals(gateway.provider())
					|| NexHomeManualPaymentGateway.PROVIDER.equals(order.getProvider())) {
				continue;
			}
			NexHomePaymentGateway.OrderStatus status = gateway.fetchOrder(order.getProviderOrderId());
			if (status == null) {
				continue;
			}
			settlement.recordAttempts(order, status.attempts(), actingUserId);
			if (NexHomePaymentGateway.OrderStatus.PAID.equals(status.status())) {
				try {
					settlement.capture(order, status.providerPaymentId(), status.paymentMode(), actingUserId);
				}
				catch (InvalidRequestException e) {
					log.warn("Paid order {} could not be captured: {}", order.getPublicId(), e.getMessage());
				}
			}
			else if (NexHomePaymentGateway.OrderStatus.EXPIRED.equals(status.status())) {
				order.setPaymentStatus(PaymentOrderStatus.EXPIRED);
				order.setUpdatedBy(actingUserId);
			}
			if (last == null) {
				last = status;
			}
		}
		return last;
	}

	private com.nexhome.app.dto.NexHomeBookingPaymentStatusResponse paymentStatusOf(NexHomeBooking booking,
			NexHomePaymentGateway.OrderStatus last) {
		boolean open = awaitingOnline(booking) && orderRepository.findByCheckoutIdOrderByIdDesc(booking.getCheckoutId())
				.stream().anyMatch(order -> PAYABLE_ORDERS.contains(order.getPaymentStatus()));
		return new com.nexhome.app.dto.NexHomeBookingPaymentStatusResponse(booking.getId(), booking.getPublicId(),
				booking.getBookingStatus(), booking.getPaymentMethod(), booking.getPaymentStatus(),
				booking.getTotalAmount(), booking.getCurrency(), open,
				last == null ? null : last.attemptStatus(), last == null ? null : last.failureMessage());
	}

	/** Paid orders of the last 30 days whose charges or settlement are still to come, asked for in turn. */
	private static final java.time.Duration CHARGES_WINDOW = java.time.Duration.ofDays(30);

	@Override
	@Transactional
	public int refreshGatewayCharges() {
		if (NexHomeManualPaymentGateway.PROVIDER.equals(gateway.provider())) {
			return 0;
		}
		int recorded = 0;
		java.util.Set<Long> asked = new java.util.HashSet<>();
		for (var attempt : attemptRepository.findUnsettled(gateway.provider(), Instant.now().minus(CHARGES_WINDOW),
				PageRequest.of(0, 50))) {
			NexHomePaymentOrder order = attempt.getPaymentOrder();
			if (!asked.add(order.getId())) {
				continue;
			}
			if (settlement.recordCharges(order, gateway.fetchCharges(order.getProviderOrderId()), SYSTEM_USER_ID)
					&& attempt.getChargesRecordedAt() != null) {
				recorded++;
			}
		}
		return recorded;
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomePaymentOrderResponse> getOrders(Integer page, Integer size, String status,
			String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomePaymentOrder> orders = orderRepository.findAll(NexHomePaymentOrderRepository.orderSpec(
				optionalCode(Types.PAYMENT_ORDER_STATUS, status, "Payment order status"),
				NexHomePaging.searchText(search)), pageRequest);
		// The online bookings each order pays for (its checkout's), one query for the page
		Map<java.util.UUID, List<NexHomePaymentOrderResponse.OrderBooking>> bookingsByCheckout = new LinkedHashMap<>();
		List<java.util.UUID> checkoutIds = orders.stream().map(NexHomePaymentOrder::getCheckoutId).distinct().toList();
		if (!checkoutIds.isEmpty()) {
			for (NexHomeBooking booking : bookingRepository.findByCheckoutIdInAndPaymentMethodOrderByIdAsc(checkoutIds,
					com.nexhome.app.model.NexHomeLookupCodes.PaymentMethod.ONLINE)) {
				bookingsByCheckout.computeIfAbsent(booking.getCheckoutId(), key -> new ArrayList<>())
						.add(new NexHomePaymentOrderResponse.OrderBooking(booking.getId(), booking.getPublicId()));
			}
		}
		return NexHomePaging.toPageResponse(pageRequest, orders, orders.stream()
				.map(order -> paymentMapper.toOrderDto(order)
						.withBookings(bookingsByCheckout.getOrDefault(order.getCheckoutId(), List.of())))
				.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePaymentStatsResponse getStats() {
		Object[] captured = paymentRepository.capturedTotals().get(0);
		Object[] refunded = refundRepository.succeededTotals().get(0);
		Object[] cash = cashCollectionRepository.dueTotals().get(0);
		Object[] cashCollected = cashCollectionRepository.collectedTotals().get(0);
		Object[] pending = bookingRepository.pendingPaymentTotals().get(0);

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
				revenue.getOrDefault(current, BigDecimal.ZERO), months, attemptRepository.gatewayChargesTotal(),
				(BigDecimal) cashCollected[0], ((Number) cashCollected[1]).longValue(),
				(BigDecimal) pending[0], ((Number) pending[1]).longValue(),
				orderRepository.sumAmountByPaymentStatusIn(List.copyOf(OPEN_ORDERS)));
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
			String status, String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		var cash = cashCollectionRepository.findAll(com.nexhome.app.repository.NexHomeCashCollectionRepository.cashSpec(
				optionalCode(Types.CASH_COLLECTION_STATUS, status, "Cash collection status"),
				NexHomePaging.searchText(search)), pageRequest);
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
		if (CashCollectionStatus.DISPUTED.equals(target)) {
			notifier.notifyStaff(NexHomeSettingsService.Keys.ALERT_CASH_DISPUTED, PAYMENT_MANAGE, new NexHomeNotifier.Message(
					com.nexhome.app.model.NexHomeLookupCodes.NotificationType.PAYMENT,
					"Cash disputed: " + booking.getPublicId(),
					"The cash of " + cash.getAmount().toPlainString() + " is disputed"
							+ (NexHomeText.trimToNull(request.notes()) == null ? "."
									: ": " + NexHomeText.trimToNull(request.notes())),
					NexHomeNotifier.BOOKING, booking.getId()), actingUserId);
		}
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
			// The customer confirms paying it (PUT /cash-collections/acknowledge; the app's Confirm your payment)
			if (cash.getCustomerAcknowledgedAt() == null) {
				notifier.notify(booking.getCustomer().getUser().getId(), new NexHomeNotifier.Message(
						com.nexhome.app.model.NexHomeLookupCodes.NotificationType.PAYMENT,
						"Confirm your cash payment " + booking.getPublicId(),
						"Your professional marked " + cash.getAmount().toPlainString() + " " + booking.getCurrency()
								+ " collected in cash for " + booking.getServiceNameSnapshot()
								+ ". Open the booking to confirm you paid.",
						NexHomeNotifier.BOOKING, booking.getId()).forApp(NexHomeNotifier.CUSTOMER), actingUserId);
			}
		}
		cash.setUpdatedBy(actingUserId);
		// A cash job waits IN_PROGRESS after its work is finished; the cash settles it
		if (CashCollectionStatus.COLLECTED.equals(target) || CashCollectionStatus.WAIVED.equals(target)) {
			settlement.completeAfterCash(booking, actingUserId);
		}
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
