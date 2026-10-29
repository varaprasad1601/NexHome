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

	// ------------------------------------------------------------------------------------------------------------
	// Payments
	// ------------------------------------------------------------------------------------------------------------

	/** The online bookings of the order's checkout that still need paying (not cancelled). */
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
			if (BookingStatus.PENDING.equals(status)) {
				booking.setBookingStatus(BookingStatus.CONFIRMED);
				bookingCreator.recordStatus(booking, BookingStatus.PENDING, BookingStatus.CONFIRMED, PAYMENT_RECEIVED,
						actingUserId);
			}
			else {
				bookingCreator.recordStatus(booking, status, status, PAYMENT_RECEIVED, actingUserId);
			}
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
			throw new InvalidRequestException("Refunds cannot exceed the captured amount less the transaction fee; "
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
		// The MANUAL gateway refunds at once; a real one answers later through a webhook
		refund.setProviderRefundId(gateway.refund(payment.getProviderPaymentId(), amount));
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
