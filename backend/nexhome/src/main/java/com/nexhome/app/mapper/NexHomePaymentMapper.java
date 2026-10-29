package com.nexhome.app.mapper;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeCashCollectionResponse;
import com.nexhome.app.dto.NexHomePaymentOrderResponse;
import com.nexhome.app.dto.NexHomePaymentResponse;
import com.nexhome.app.dto.NexHomePayoutResponse;
import com.nexhome.app.dto.NexHomeRefundResponse;
import com.nexhome.app.dto.NexHomeTransferResponse;
import com.nexhome.app.dto.NexHomeWebhookEventResponse;
import com.nexhome.app.model.NexHomeBooking;
import com.nexhome.app.model.NexHomeCashCollection;
import com.nexhome.app.model.NexHomePartnerPayout;
import com.nexhome.app.model.NexHomePartnerTransfer;
import com.nexhome.app.model.NexHomePayment;
import com.nexhome.app.model.NexHomePaymentOrder;
import com.nexhome.app.model.NexHomePaymentRefund;
import com.nexhome.app.model.NexHomePaymentWebhookEvent;

/** Payment orders, payments, refunds, cash collections, webhook events, payouts and transfers. */
@Component
public class NexHomePaymentMapper {

	public NexHomePaymentOrderResponse toOrderDto(NexHomePaymentOrder order) {
		return new NexHomePaymentOrderResponse(order.getId(), order.getPublicId(), order.getCheckoutId(),
				order.getCustomer().getId(),
				NexHomeBookingMapper.nameOf(order.getCustomer().getFirstName(), order.getCustomer().getLastName()),
				order.getProvider(), order.getProviderOrderId(), order.getAmount(), order.getCurrency(),
				order.getPaymentStatus(), order.getExpiresAt(), order.getCreationDate());
	}

	public NexHomePaymentResponse toPaymentDto(NexHomePayment payment, BigDecimal refundedAmount) {
		NexHomeBooking booking = payment.getBooking();
		return new NexHomePaymentResponse(payment.getId(), payment.getPaymentOrder().getId(),
				payment.getPaymentOrder().getPublicId(), booking.getId(), booking.getPublicId(),
				booking.getCustomer().getId(),
				NexHomeBookingMapper.nameOf(booking.getCustomer().getFirstName(), booking.getCustomer().getLastName()),
				booking.getTotalAmount(), payment.getProvider(), payment.getProviderPaymentId(), payment.getPaymentMethod(),
				payment.getAmount(),
				refundedAmount, payment.getCurrency(), payment.getStatus(), payment.getFailureCode(),
				payment.getFailureMessage(), payment.getCapturedAt(), payment.getCreationDate());
	}

	public NexHomeRefundResponse toRefundDto(NexHomePaymentRefund refund) {
		NexHomeBooking booking = refund.getBooking();
		return new NexHomeRefundResponse(refund.getId(), refund.getPayment().getId(), booking.getId(),
				booking.getPublicId(),
				NexHomeBookingMapper.nameOf(booking.getCustomer().getFirstName(), booking.getCustomer().getLastName()),
				refund.getProviderRefundId(), refund.getAmount(), refund.getReason(), refund.getStatus(),
				refund.getRequestedBy(), refund.getCompletedAt(), refund.getCreationDate());
	}

	public NexHomeCashCollectionResponse toCashDto(NexHomeCashCollection cash) {
		NexHomeBooking booking = cash.getBooking();
		return new NexHomeCashCollectionResponse(cash.getId(), booking.getId(), booking.getPublicId(),
				booking.getCustomer().getId(),
				NexHomeBookingMapper.nameOf(booking.getCustomer().getFirstName(), booking.getCustomer().getLastName()),
				booking.getPartner() == null ? null : booking.getPartner().getId(),
				booking.getPartner() == null ? null
						: NexHomeBookingMapper.nameOf(booking.getPartner().getFirstName(),
								booking.getPartner().getLastName()),
				cash.getAmount(), cash.getStatus(), cash.getCollectedBy(), cash.getConfirmedAt(),
				cash.getCustomerAcknowledgedAt(), cash.getNotes(), cash.getCreationDate());
	}

	public NexHomeWebhookEventResponse toWebhookDto(NexHomePaymentWebhookEvent event) {
		return new NexHomeWebhookEventResponse(event.getId(), event.getProvider(), event.getProviderEventId(),
				event.getEventType(), event.isSignatureVerified(), event.getProcessingStatus(), event.getReceivedAt(),
				event.getProcessedAt(), event.getProcessingError());
	}

	public NexHomePayoutResponse toPayoutDto(NexHomePartnerPayout payout) {
		return new NexHomePayoutResponse(payout.getId(), payout.getPublicId(), payout.getPartner().getId(),
				payout.getPartner().getPublicId(),
				NexHomeBookingMapper.nameOf(payout.getPartner().getFirstName(), payout.getPartner().getLastName()),
				payout.getBooking().getId(), payout.getBooking().getPublicId(),
				payout.getPayment() == null ? null : payout.getPayment().getId(), payout.getGrossAmount(),
				payout.getPlatformCommission(), payout.getAdjustmentsAmount(), payout.getNetAmount(),
				payout.getProviderTransferId(), payout.getPayoutStatus(), payout.getScheduledAt(), payout.getPaidAt(),
				payout.getCreationDate());
	}

	public NexHomeTransferResponse toTransferDto(NexHomePartnerTransfer transfer) {
		NexHomePartnerPayout payout = transfer.getPayout();
		return new NexHomeTransferResponse(transfer.getId(), payout.getId(), payout.getPublicId(),
				payout.getPartner().getId(),
				NexHomeBookingMapper.nameOf(payout.getPartner().getFirstName(), payout.getPartner().getLastName()),
				transfer.getProviderTransferId(), transfer.getAmount(), transfer.getStatus(), transfer.getSettledAt(),
				transfer.getCreationDate());
	}

}
