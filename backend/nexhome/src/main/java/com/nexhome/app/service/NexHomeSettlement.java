package com.nexhome.app.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeBookingMapper;
import com.nexhome.app.model.NexHomeBooking;
import com.nexhome.app.model.NexHomeCashCollection;
import com.nexhome.app.model.NexHomeLookupCodes.BookingPaymentStatus;
import com.nexhome.app.model.NexHomeLookupCodes.BookingStatus;
import com.nexhome.app.model.NexHomeLookupCodes.CashCollectionStatus;
import com.nexhome.app.model.NexHomeLookupCodes.NotificationType;
import com.nexhome.app.model.NexHomeLookupCodes.PaymentMethod;
import com.nexhome.app.model.NexHomeLookupCodes.PaymentOrderStatus;
import com.nexhome.app.model.NexHomeLookupCodes.PaymentStatus;
import com.nexhome.app.model.NexHomeLookupCodes.PayoutStatus;
import com.nexhome.app.model.NexHomeLookupCodes.RefundStatus;
import com.nexhome.app.model.NexHomePartnerPayout;
import com.nexhome.app.model.NexHomePayment;
import com.nexhome.app.model.NexHomePaymentAttempt;
import com.nexhome.app.model.NexHomePaymentOrder;
import com.nexhome.app.model.NexHomePaymentRefund;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.repository.NexHomeBookingRepository;
import com.nexhome.app.repository.NexHomeCashCollectionRepository;
import com.nexhome.app.repository.NexHomePartnerPayoutRepository;
import com.nexhome.app.repository.NexHomePaymentRefundRepository;
import com.nexhome.app.repository.NexHomePaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The money side of bookings (Module 7), shared by the booking, payment and payout services and run inside their
 * transactions: capturing an order's payments, refunds (never more than was captured), cash collections for cash
 * bookings, partner payouts for paid online bookings and the service's commission.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class NexHomeSettlement {

	/** Payments money can still be refunded from. */
	static final Set<String> REFUNDABLE = Set.of(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED);
	/** Payouts that are not paid or on the way, so they can still change. */
	static final Set<String> OPEN_PAYOUTS = Set.of(PayoutStatus.PENDING, PayoutStatus.ON_HOLD, PayoutStatus.FAILED);

	/** History reasons of payment events (V43): a row whose status stays the same is a payment, not a change. */
	static final String PAYMENT_RECEIVED = "Payment received";
	static final String CASH_COLLECTED = "Cash collected";

	private final NexHomeBookingRepository bookingRepository;
	private final NexHomePaymentRepository paymentRepository;
	private final NexHomePaymentRefundRepository refundRepository;
	private final NexHomePartnerPayoutRepository payoutRepository;
	private final NexHomeCashCollectionRepository cashCollectionRepository;
	private final NexHomePaymentGateway gateway;
	private final NexHomeBookingCreator bookingCreator;
	private final NexHomeNotifier notifier;
	private final NexHomeAuditRecorder auditRecorder;
	private final com.nexhome.app.repository.NexHomeBookingAssignmentRepository assignmentRepository;
	private final com.nexhome.app.repository.NexHomePaymentAttemptRepository attemptRepository;

	// ------------------------------------------------------------------------------------------------------------
	// Payments
	// ------------------------------------------------------------------------------------------------------------

	/** The online bookings of the order's checkout that still need paying (not cancelled). */
	/** How a payment group is shown in history lines. */
	private static final Map<String, String> MODE_NAMES = Map.ofEntries(Map.entry("upi", "UPI"),
			Map.entry("credit_card", "Credit Card"), Map.entry("debit_card", "Debit Card"),
			Map.entry("prepaid_card", "Prepaid Card"), Map.entry("net_banking", "Net Banking"),
			Map.entry("wallet", "Wallet"), Map.entry("pay_later", "Pay Later"),
			Map.entry("cardless_emi", "Cardless EMI"), Map.entry("credit_card_emi", "Card EMI"),
			Map.entry("debit_card_emi", "Card EMI"), Map.entry("bank_transfer", "Bank Transfer"));

	/** History headings of attempt statuses (SUCCESS is written by capture as PAYMENT_RECEIVED). */
	private static final Map<String, String> ATTEMPT_EVENTS = Map.of("FAILED", "Payment failed", "PENDING",
			"Payment pending", "USER_DROPPED", "Payment cancelled", "CANCELLED", "Payment cancelled", "VOID",
			"Payment cancelled");

	/**
	 * Keeps the gateway's attempts of an order (V67): new ones added, changed ones updated. Each new or changed
	 * failed, pending or cancelled attempt is a line in the history of the order's online bookings (status unchanged,
	 * e.g. "Payment failed · ₹412.50 · UPI · Ref 5114911 · Insufficient balance"), so the customer, the partner and
	 * staff see it. Successful ones show when captured.
	 */
	void recordAttempts(NexHomePaymentOrder order, List<NexHomePaymentGateway.Attempt> attempts, Long actingUserId) {
		if (attempts == null || attempts.isEmpty()) {
			return;
		}
		List<NexHomeBooking> bookings = null;
		for (NexHomePaymentGateway.Attempt attempt : attempts) {
			if (attempt.providerPaymentId() == null || attempt.status() == null) {
				continue;
			}
			NexHomePaymentAttempt row = attemptRepository
					.findByProviderAndProviderPaymentId(order.getProvider(), attempt.providerPaymentId()).orElse(null);
			boolean changed = row == null || !attempt.status().equals(row.getAttemptStatus());
			if (row == null) {
				row = new NexHomePaymentAttempt();
				row.setPaymentOrder(order);
				row.setProvider(order.getProvider());
				row.setProviderPaymentId(truncate(attempt.providerPaymentId(), 150));
				row.setCreatedBy(actingUserId);
			}
			row.setAttemptStatus(truncate(attempt.status(), 40));
			row.setPaymentMode(truncate(attempt.paymentMode(), 40));
			row.setPaymentMethodDetail(truncate(attempt.methodDetail(), 200));
			row.setAmount(attempt.amount() != null ? attempt.amount() : order.getAmount());
			row.setCurrency(attempt.currency() == null ? order.getCurrency() : attempt.currency());
			row.setBankReference(truncate(attempt.bankReference(), 150));
			row.setErrorCode(truncate(attempt.errorCode(), 100));
			row.setMessage(attempt.message());
			row.setAttemptedAt(attempt.attemptedAt() != null ? attempt.attemptedAt() : row.getAttemptedAt());
			row.setCompletedAt(attempt.completedAt() != null ? attempt.completedAt() : row.getCompletedAt());
			// What the customer paid the gateway on top (V69), when the gateway says
			boolean surchargeChanged = false;
			if (attempt.surcharge() != null || attempt.surchargeTax() != null) {
				BigDecimal charge = money(attempt.surcharge());
				BigDecimal tax = money(attempt.surchargeTax());
				surchargeChanged = row.getSurchargeAmount() == null || charge.compareTo(row.getSurchargeAmount()) != 0
						|| row.getSurchargeTax() == null || tax.compareTo(row.getSurchargeTax()) != 0;
				row.setSurchargeAmount(charge);
				row.setSurchargeTax(tax);
			}
			row.setUpdatedBy(actingUserId);
			if (row.getId() == null) {
				attemptRepository.save(row);
			}
			if (surchargeChanged && "SUCCESS".equals(row.getAttemptStatus())
					&& PaymentOrderStatus.PAID.equals(order.getPaymentStatus())) {
				applySurcharge(order, row);
			}
			String event = ATTEMPT_EVENTS.get(attempt.status());
			if (changed && event != null) {
				if (bookings == null) {
					bookings = bookingRepository.findByCheckoutIdOrderByIdAsc(order.getCheckoutId()).stream()
							.filter(booking -> PaymentMethod.ONLINE.equals(booking.getPaymentMethod()))
							.filter(booking -> !BookingStatus.CANCELLED.equals(booking.getBookingStatus()))
							.toList();
				}
				for (NexHomeBooking booking : bookings) {
					String status = booking.getBookingStatus();
					bookingCreator.recordStatus(booking, status, status,
							paymentLine(event, booking.getTotalAmount(), attempt.paymentMode(), attempt.providerPaymentId(),
									attempt.message()),
							actingUserId);
				}
			}
		}
	}

	/**
	 * Keeps what the gateway took for an order's successful attempt (V68): its charge and tax, what it pays NexHome
	 * and the settlement; the charges are shared among the order's payments by amount. Returns false when the order
	 * has no successful attempt to put them on.
	 */
	boolean recordCharges(NexHomePaymentOrder order, NexHomePaymentGateway.Charges charges, Long actingUserId) {
		if (charges == null) {
			return false;
		}
		NexHomePaymentAttempt attempt = (charges.providerPaymentId() == null ? Optional.<NexHomePaymentAttempt>empty()
				: attemptRepository.findByProviderAndProviderPaymentId(order.getProvider(), charges.providerPaymentId())
						.filter(found -> found.getPaymentOrder().getId().equals(order.getId())))
				.or(() -> attemptRepository.findByPaymentOrderIdOrderByAttemptedAtDescIdDesc(order.getId()).stream()
						.filter(found -> "SUCCESS".equals(found.getAttemptStatus())).findFirst())
				.orElse(null);
		if (attempt == null) {
			return false;
		}
		boolean chargesGiven = charges.serviceCharge() != null || charges.serviceTax() != null;
		BigDecimal before = chargesOf(attempt);
		if (chargesGiven) {
			attempt.setGatewayCharge(money(charges.serviceCharge()));
			attempt.setGatewayTax(money(charges.serviceTax()));
			attempt.setChargesRecordedAt(Instant.now());
		}
		if (charges.settlementAmount() != null) {
			attempt.setSettlementAmount(charges.settlementAmount().setScale(2, java.math.RoundingMode.HALF_UP));
		}
		if (charges.settlementCurrency() != null && charges.settlementCurrency().length() == 3) {
			attempt.setSettlementCurrency(charges.settlementCurrency());
		}
		if (charges.settlementId() != null) {
			attempt.setSettlementId(truncate(charges.settlementId(), 100));
			attempt.setSettlementUtr(truncate(charges.settlementUtr(), 100));
			attempt.setSettledAt(charges.settledAt());
		}
		attempt.setUpdatedBy(actingUserId);
		BigDecimal after = chargesOf(attempt);
		if (chargesGiven && (before == null || before.compareTo(after) != 0)) {
			shareCharges(order, after);
			auditRecorder.record("GATEWAY_CHARGES_RECORDED", "PAYMENT_ORDER", order.getPublicId(),
					before == null ? null : NexHomeAuditRecorder.values("charges", before),
					NexHomeAuditRecorder.values("charge", attempt.getGatewayCharge(), "tax", attempt.getGatewayTax(),
							"settlementAmount", attempt.getSettlementAmount(), "providerPaymentId",
							attempt.getProviderPaymentId()),
					actingUserId);
			log.info("Gateway charges {} recorded for order {}", after, order.getPublicId());
		}
		return true;
	}

	/**
	 * The surcharge the customer paid on the order's successful attempt (V69), shared among the order's payments and
	 * bookings by amount: the payment's customerChargesAmount, the booking's gateway service charge and tax. Kept
	 * apart from the bookings' totals, so revenue, refunds and payouts never include it.
	 */
	private void applySurcharge(NexHomePaymentOrder order, NexHomePaymentAttempt attempt) {
		BigDecimal charge = money(attempt.getSurchargeAmount());
		BigDecimal tax = money(attempt.getSurchargeTax());
		List<NexHomePayment> payments = paymentRepository.findByPaymentOrderIdOrderByIdAsc(order.getId());
		BigDecimal sum = payments.stream().map(NexHomePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal chargeLeft = charge;
		BigDecimal taxLeft = tax;
		for (int i = 0; i < payments.size(); i++) {
			NexHomePayment payment = payments.get(i);
			boolean last = i == payments.size() - 1 || sum.signum() == 0;
			BigDecimal chargeShare = last ? chargeLeft
					: charge.multiply(payment.getAmount()).divide(sum, 2, java.math.RoundingMode.HALF_UP).min(chargeLeft);
			BigDecimal taxShare = last ? taxLeft
					: tax.multiply(payment.getAmount()).divide(sum, 2, java.math.RoundingMode.HALF_UP).min(taxLeft);
			chargeLeft = chargeLeft.subtract(chargeShare);
			taxLeft = taxLeft.subtract(taxShare);
			payment.setCustomerChargesAmount(chargeShare.add(taxShare));
			NexHomeBooking booking = payment.getBooking();
			booking.setGatewayServiceCharge(chargeShare);
			booking.setGatewayServiceTax(taxShare);
		}
		if (charge.add(tax).signum() > 0) {
			log.info("Order {}: the customer paid {} gateway charges and {} GST on top", order.getPublicId(), charge,
					tax);
		}
	}

	/** An attempt's charge + tax, null until the gateway gave them. */
	private static BigDecimal chargesOf(NexHomePaymentAttempt attempt) {
		if (attempt.getChargesRecordedAt() == null) {
			return null;
		}
		return money(attempt.getGatewayCharge()).add(money(attempt.getGatewayTax()));
	}

	private static BigDecimal money(BigDecimal amount) {
		return amount == null ? BigDecimal.ZERO.setScale(2) : amount.max(BigDecimal.ZERO).setScale(2,
				java.math.RoundingMode.HALF_UP);
	}

	/** The order's charges over its payments, by amount (the last takes what rounding leaves). */
	private void shareCharges(NexHomePaymentOrder order, BigDecimal total) {
		List<NexHomePayment> payments = paymentRepository.findByPaymentOrderIdOrderByIdAsc(order.getId());
		BigDecimal sum = payments.stream().map(NexHomePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal left = total;
		for (int i = 0; i < payments.size(); i++) {
			NexHomePayment payment = payments.get(i);
			BigDecimal share = i == payments.size() - 1 || sum.signum() == 0 ? left
					: total.multiply(payment.getAmount()).divide(sum, 2, java.math.RoundingMode.HALF_UP).min(left);
			payment.setGatewayChargesAmount(share);
			left = left.subtract(share);
		}
	}

	/** A payment's history line: what happened, amount, how, the gateway's reference and its message. */
	static String paymentLine(String event, BigDecimal amount, String paymentMode, String reference, String message) {
		StringBuilder line = new StringBuilder(event);
		if (amount != null) {
			line.append(" · ₹").append(amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
		}
		if (paymentMode != null) {
			line.append(" · ").append(MODE_NAMES.getOrDefault(paymentMode, paymentMode.replace('_', ' ')));
		}
		if (reference != null) {
			line.append(" · Ref ").append(reference);
		}
		if (message != null && !message.isBlank()) {
			line.append(" · ").append(message.trim());
		}
		return truncate(line.toString(), 1000);
	}

	private static String truncate(String text, int length) {
		return text == null || text.length() <= length ? text : text.substring(0, length);
	}

	List<NexHomeBooking> unpaidBookingsOf(NexHomePaymentOrder order) {
		return unpaidBookingsOf(order.getCheckoutId());
	}

	List<NexHomeBooking> unpaidBookingsOf(java.util.UUID checkoutId) {
		return bookingRepository.findByCheckoutIdOrderByIdAsc(checkoutId).stream()
				.filter(booking -> PaymentMethod.ONLINE.equals(booking.getPaymentMethod()))
				// Cancelled first: they have no payment status (V42), and Set.of(...).contains(null) throws
				.filter(booking -> !BookingStatus.CANCELLED.equals(booking.getBookingStatus()))
				.filter(booking -> Set.of(BookingPaymentStatus.PENDING, BookingPaymentStatus.FAILED)
						.contains(booking.getPaymentStatus()))
				.toList();
	}

	/**
	 * The order was paid: one CAPTURED payment per booking, the bookings PAID (a PENDING one CONFIRMED), payouts for
	 * bookings already completed. Capturing a PAID order again changes nothing.
	 */
	List<NexHomePayment> capture(NexHomePaymentOrder order, String providerPaymentId, Long actingUserId) {
		return capture(order, providerPaymentId, null, actingUserId);
	}

	/** As above, with how it was paid ({@code paymentMode}: the gateway's payment group, or null). */
	List<NexHomePayment> capture(NexHomePaymentOrder order, String providerPaymentId, String paymentMode,
			Long actingUserId) {
		if (PaymentOrderStatus.PAID.equals(order.getPaymentStatus())) {
			return paymentRepository.findByPaymentOrderIdOrderByIdAsc(order.getId());
		}
		if (!Set.of(PaymentOrderStatus.CREATED, PaymentOrderStatus.PENDING, PaymentOrderStatus.AUTHORIZED)
				.contains(order.getPaymentStatus())) {
			throw new InvalidRequestException("A payment order that is " + order.getPaymentStatus()
					+ " cannot be paid; create a new one.");
		}
		List<NexHomeBooking> bookings = unpaidBookingsOf(order);
		BigDecimal due = bookings.stream().map(NexHomeBooking::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (bookings.isEmpty() || due.compareTo(order.getAmount()) != 0) {
			throw new InvalidRequestException("The order no longer matches its bookings; create a new one.");
		}
		Instant now = Instant.now();
		String attemptReference = providerPaymentId != null ? providerPaymentId : order.getProviderOrderId() + "_pay";
		// The successful attempt (V67), unless the gateway's list already gave it
		if (attemptRepository.findByProviderAndProviderPaymentId(order.getProvider(), attemptReference).isEmpty()) {
			recordAttempts(order, List.of(new NexHomePaymentGateway.Attempt(attemptReference, "SUCCESS", paymentMode,
					null, order.getAmount(), order.getCurrency(), null, null, null, now, now)), actingUserId);
		}
		List<NexHomePayment> payments = new ArrayList<>();
		for (int i = 0; i < bookings.size(); i++) {
			NexHomeBooking booking = bookings.get(i);
			NexHomePayment payment = new NexHomePayment();
			payment.setPaymentOrder(order);
			payment.setBooking(booking);
			payment.setProvider(order.getProvider());
			// The provider reference is unique; an order's bookings share it with a suffix
			String reference = providerPaymentId != null ? providerPaymentId : order.getProviderOrderId() + "_pay";
			payment.setProviderPaymentId(bookings.size() == 1 ? reference : reference + "_" + (i + 1));
			payment.setPaymentMethod(PaymentMethod.ONLINE);
			payment.setPaymentMode(paymentMode);
			payment.setAmount(booking.getTotalAmount());
			payment.setCurrency(booking.getCurrency());
			payment.setStatus(PaymentStatus.CAPTURED);
			payment.setCapturedAt(now);
			payment.setCreatedBy(actingUserId);
			payment.setUpdatedBy(actingUserId);
			payment = paymentRepository.save(payment);
			payments.add(payment);

			booking.setPaymentStatus(BookingPaymentStatus.PAID);
			booking.setUpdatedBy(actingUserId);
			// The booking's history shows the payment: with the confirmation it brings, or on its own (same status)
			String status = booking.getBookingStatus();
			String line = paymentLine(PAYMENT_RECEIVED, booking.getTotalAmount(), paymentMode, providerPaymentId, null);
			if (BookingStatus.PENDING.equals(status)) {
				booking.setBookingStatus(BookingStatus.CONFIRMED);
				bookingCreator.recordStatus(booking, BookingStatus.PENDING, BookingStatus.CONFIRMED, line, actingUserId);
			}
			else {
				bookingCreator.recordStatus(booking, status, status, line, actingUserId);
			}
			// A job whose work is finished waits IN_PROGRESS for its payment: this completes it
			completeAfterCash(booking, actingUserId);
			if (BookingStatus.COMPLETED.equals(booking.getBookingStatus())) {
				ensurePayout(booking, payment, actingUserId);
			}
			notifier.notify(booking.getCustomer().getUser().getId(), new NexHomeNotifier.Message(
					NotificationType.PAYMENT, "Payment received for " + booking.getPublicId(),
					"We received " + payment.getAmount().toPlainString() + " " + payment.getCurrency() + " for "
							+ booking.getServiceNameSnapshot() + ".",
					NexHomeNotifier.BOOKING, booking.getId()), actingUserId);
			auditRecorder.record("PAYMENT_CAPTURED", "BOOKING", booking.getPublicId(), null,
					Map.of("amount", payment.getAmount(), "reference", payment.getProviderPaymentId()), actingUserId);
		}
		order.setPaymentStatus(PaymentOrderStatus.PAID);
		order.setUpdatedBy(actingUserId);
		// Charges the gateway reported before the capture (V68) go to the new payments, and so does the surcharge the
		// customer paid (V69)
		attemptRepository.findByProviderAndProviderPaymentId(order.getProvider(), attemptReference).ifPresent(paid -> {
			BigDecimal charges = chargesOf(paid);
			if (charges != null) {
				shareCharges(order, charges);
			}
			if (paid.getSurchargeAmount() != null || paid.getSurchargeTax() != null) {
				applySurcharge(order, paid);
			}
		});
		log.info("Payment order {} paid: {} booking(s)", order.getPublicId(), payments.size());
		return payments;
	}

	/** The gateway reported a failed payment: the order FAILED, its bookings' payment FAILED (they stay PENDING). */
	void fail(NexHomePaymentOrder order, String code, String message, Long actingUserId) {
		if (PaymentOrderStatus.PAID.equals(order.getPaymentStatus())) {
			return;
		}
		for (NexHomeBooking booking : unpaidBookingsOf(order)) {
			NexHomePayment payment = new NexHomePayment();
			payment.setPaymentOrder(order);
			payment.setBooking(booking);
			payment.setProvider(order.getProvider());
			payment.setPaymentMethod(PaymentMethod.ONLINE);
			payment.setAmount(booking.getTotalAmount());
			payment.setCurrency(booking.getCurrency());
			payment.setStatus(PaymentStatus.FAILED);
			payment.setFailureCode(code);
			payment.setFailureMessage(message);
			payment.setCreatedBy(actingUserId);
			payment.setUpdatedBy(actingUserId);
			paymentRepository.save(payment);
			booking.setPaymentStatus(BookingPaymentStatus.FAILED);
			booking.setUpdatedBy(actingUserId);
			notifier.notifyStaff(NexHomeSettingsService.Keys.ALERT_PAYMENT_FAILED, "PAYMENT_MANAGE", new NexHomeNotifier.Message(NotificationType.PAYMENT,
					"Payment failed: " + booking.getPublicId(),
					"The online payment for " + booking.getServiceNameSnapshot() + " failed"
							+ (message == null ? "." : ": " + message),
					NexHomeNotifier.BOOKING, booking.getId()), actingUserId);
		}
		order.setPaymentStatus(PaymentOrderStatus.FAILED);
		order.setUpdatedBy(actingUserId);
		log.info("Payment order {} failed: {}", order.getPublicId(), code);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Refunds
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Refunds from a captured payment (locked while it is counted): never more than was captured, and not once the
	 * partner's payout for the booking is paid. The payment and booking become PARTIALLY_REFUNDED or REFUNDED and an
	 * open payout is worked out again for what is left.
	 */
	NexHomePaymentRefund refund(Long paymentId, BigDecimal amount, String reason, Long actingUserId) {
		NexHomePayment payment = paymentRepository.findByIdForUpdate(paymentId)
				.orElseThrow(() -> new ResourceNotFoundException("Payment does not exist."));
		if (!REFUNDABLE.contains(payment.getStatus())) {
			throw new InvalidRequestException("A payment that is " + payment.getStatus() + " cannot be refunded.");
		}
		NexHomeBooking booking = payment.getBooking();
		BigDecimal refundable = refundable(payment);
		BigDecimal refunded = refundRepository.sumOpenOrSucceeded(paymentId);
		BigDecimal left = refundable.subtract(refunded);
		if (amount.compareTo(left) > 0) {
			// Bookings made before V68 paid an online payment fee, which is not refunded
			boolean feePaid = booking.getTransactionFeeAmount() != null && booking.getTransactionFeeAmount().signum() > 0;
			throw new InvalidRequestException("Refunds cannot exceed the captured amount"
					+ (feePaid ? " less the online payment fee" : "") + "; "
					+ left.max(BigDecimal.ZERO).toPlainString() + " can still be refunded.");
		}
		Optional<NexHomePartnerPayout> payout = payoutRepository.findByBookingId(booking.getId());
		if (payout.isPresent() && !OPEN_PAYOUTS.contains(payout.get().getPayoutStatus())
				&& !PayoutStatus.CANCELLED.equals(payout.get().getPayoutStatus())) {
			throw new InvalidRequestException("The partner's payout for this booking is already "
					+ payout.get().getPayoutStatus() + "; the booking cannot be refunded here.");
		}

		NexHomePaymentRefund refund = new NexHomePaymentRefund();
		refund.setPayment(payment);
		refund.setBooking(booking);
		refund.setAmount(amount);
		refund.setReason(reason);
		refund.setStatus(RefundStatus.REQUESTED);
		refund.setRequestedBy(actingUserId);
		refund.setCreatedBy(actingUserId);
		refund.setUpdatedBy(actingUserId);
		refund = refundRepository.save(refund);
		// The MANUAL gateway refunds at once; Cashfree accepts it and pays it back to the customer in a few days
		refund.setProviderRefundId(gateway.refund(payment.getPaymentOrder().getProviderOrderId(),
				payment.getProviderPaymentId(), "NHREF" + refund.getId(), amount));
		refund.setStatus(RefundStatus.SUCCEEDED);
		refund.setCompletedAt(Instant.now());

		BigDecimal nowRefunded = refunded.add(amount);
		boolean full = nowRefunded.compareTo(refundable) >= 0;
		payment.setStatus(full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
		payment.setUpdatedBy(actingUserId);
		// A cancelled booking keeps no payment status (V42); the refund is on its payment
		if (!BookingStatus.CANCELLED.equals(booking.getBookingStatus())) {
			booking.setPaymentStatus(full ? BookingPaymentStatus.REFUNDED : BookingPaymentStatus.PARTIALLY_REFUNDED);
		}
		booking.setUpdatedBy(actingUserId);
		payout.filter(open -> OPEN_PAYOUTS.contains(open.getPayoutStatus()))
				.ifPresent(open -> recompute(open, refundable.subtract(nowRefunded), actingUserId));

		notifier.notify(booking.getCustomer().getUser().getId(), new NexHomeNotifier.Message(
				NotificationType.PAYMENT, "Refund for " + booking.getPublicId(),
				"We refunded " + amount.toPlainString() + " " + payment.getCurrency() + ".", NexHomeNotifier.BOOKING,
				booking.getId()), actingUserId);
		Map<String, Object> refundValues = new java.util.LinkedHashMap<>();
		refundValues.put("amount", amount);
		refundValues.put("reason", reason);
		refundValues.put("paymentStatus", payment.getStatus());
		auditRecorder.record("REFUNDED", "BOOKING", booking.getPublicId(), null, refundValues, actingUserId);
		log.info("Refund {} of {} for booking {}", refund.getId(), amount, booking.getPublicId());
		return refund;
	}

	/**
	 * What can be refunded of a payment, and what the partner's payout is worked out from: the amount captured less
	 * the booking's transaction fee (V35), which the platform keeps.
	 */
	static BigDecimal refundable(NexHomePayment payment) {
		BigDecimal fee = payment.getBooking().getTransactionFeeAmount();
		return payment.getAmount().subtract(fee == null ? BigDecimal.ZERO : fee).max(BigDecimal.ZERO);
	}

	/**
	 * A cancelled booking's captured payment is refunded but for the transaction fee (cancellation fees come with
	 * Module 8).
	 */
	void refundOnCancel(NexHomeBooking booking, Long actingUserId) {
		paymentRepository.findFirstByBookingIdAndStatusInOrderByIdDesc(booking.getId(), REFUNDABLE)
				.ifPresent(payment -> {
					BigDecimal left = refundable(payment).subtract(refundRepository.sumOpenOrSucceeded(payment.getId()));
					if (left.signum() > 0) {
						refund(payment.getId(), left, "Booking cancelled", actingUserId);
					}
				});
	}

	// ------------------------------------------------------------------------------------------------------------
	// Completion: cash and payouts
	// ------------------------------------------------------------------------------------------------------------

	/** A completed booking: cash is due from the customer to the partner, or the partner is owed a payout. */
	void onBookingCompleted(NexHomeBooking booking, Long actingUserId) {
		if (PaymentMethod.CASH.equals(booking.getPaymentMethod())) {
			if (cashCollectionRepository.findByBookingId(booking.getId()).isEmpty()) {
				NexHomeCashCollection cash = new NexHomeCashCollection();
				cash.setBooking(booking);
				cash.setAmount(booking.getTotalAmount());
				cash.setStatus(CashCollectionStatus.DUE);
				cash.setCreatedBy(actingUserId);
				cash.setUpdatedBy(actingUserId);
				cashCollectionRepository.save(cash);
			}
			return;
		}
		paymentRepository.findFirstByBookingIdAndStatusInOrderByIdDesc(booking.getId(), REFUNDABLE)
				.ifPresent(payment -> ensurePayout(booking, payment, actingUserId));
	}

	/** The history reason of a cash job whose work is finished while its cash is still to be collected. */
	static final String WORK_FINISHED = "Work finished";

	/**
	 * A job whose work is finished (IN_PROGRESS with its end time) is completed once it is paid: its cash collected or
	 * waived, its online payment captured, or cash collected instead: the assignment COMPLETED, the booking COMPLETED (history), and the customer asked to rate it.
	 */
	void completeAfterCash(NexHomeBooking booking, Long actingUserId) {
		if (!BookingStatus.IN_PROGRESS.equals(booking.getBookingStatus()) || booking.getActualEndAt() == null) {
			return;
		}
		assignmentRepository.findFirstByBookingIdAndAssignmentStatusIn(booking.getId(),
				List.of(com.nexhome.app.model.NexHomeLookupCodes.AssignmentStatus.ASSIGNED,
						com.nexhome.app.model.NexHomeLookupCodes.AssignmentStatus.ACCEPTED,
						com.nexhome.app.model.NexHomeLookupCodes.AssignmentStatus.STARTED))
				.ifPresent(assignment -> {
					assignment.setAssignmentStatus(com.nexhome.app.model.NexHomeLookupCodes.AssignmentStatus.COMPLETED);
					assignment.setUpdatedBy(actingUserId);
				});
		String old = booking.getBookingStatus();
		booking.setBookingStatus(BookingStatus.COMPLETED);
		booking.setUpdatedBy(actingUserId);
		bookingCreator.recordStatus(booking, old, BookingStatus.COMPLETED, null, actingUserId);
		notifier.notify(booking.getCustomer().getUser().getId(), new NexHomeNotifier.Message(NotificationType.BOOKING,
				"Booking " + booking.getPublicId() + " completed",
				booking.getServiceNameSnapshot() + " is done. Rate your service and partner.", NexHomeNotifier.BOOKING,
				booking.getId()), actingUserId);
	}

	/** The partner's payout for a completed, paid online booking (once per booking). */
	void ensurePayout(NexHomeBooking booking, NexHomePayment payment, Long actingUserId) {
		if (booking.getPartner() == null || payoutRepository.findByBookingId(booking.getId()).isPresent()) {
			return;
		}
		NexHomePartnerPayout payout = new NexHomePartnerPayout();
		payout.setPartner(booking.getPartner());
		payout.setBooking(booking);
		payout.setPayment(payment);
		payout.setAdjustmentsAmount(BigDecimal.ZERO);
		payout.setPayoutStatus(PayoutStatus.PENDING);
		payout.setCreatedBy(actingUserId);
		recompute(payout, refundable(payment).subtract(refundRepository.sumOpenOrSucceeded(payment.getId())),
				actingUserId);
		if (!PayoutStatus.CANCELLED.equals(payout.getPayoutStatus())) {
			payoutRepository.save(payout);
			log.info("Payout of {} for booking {} owed to partner {}", payout.getNetAmount(), booking.getPublicId(),
					booking.getPartner().getPublicId());
		}
	}

	/** Gross from what was kept, commission by the rules at completion, net never below 0. */
	private void recompute(NexHomePartnerPayout payout, BigDecimal gross, Long actingUserId) {
		NexHomeBooking booking = payout.getBooking();
		if (gross.signum() <= 0) {
			payout.setGrossAmount(BigDecimal.ZERO);
			payout.setPlatformCommission(BigDecimal.ZERO);
			payout.setAdjustmentsAmount(BigDecimal.ZERO);
			payout.setNetAmount(BigDecimal.ZERO);
			payout.setPayoutStatus(PayoutStatus.CANCELLED);
		} else {
			BigDecimal commission = commissionFor(booking.getService(), gross);
			payout.setGrossAmount(gross);
			payout.setPlatformCommission(commission);
			BigDecimal adjustments = payout.getAdjustmentsAmount();
			if (gross.subtract(commission).add(adjustments).signum() < 0) {
				adjustments = commission.subtract(gross);
			}
			payout.setAdjustmentsAmount(adjustments);
			payout.setNetAmount(gross.subtract(commission).add(adjustments));
		}
		payout.setUpdatedBy(actingUserId);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Commission
	// ------------------------------------------------------------------------------------------------------------

	/** The platform's commission of a service: its own platform commission (no commission rules for now). */
	static BigDecimal commissionFor(NexHomeService service, BigDecimal gross) {
		return NexHomeBookingMapper.commissionOf(service, gross);
	}
}
