package com.nexhome.app.service;

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

/**
 * Module 7, money in: payment orders for online bookings, captured payments, refunds, cash collected by partners and
 * the providers' webhooks. Staff need PAYMENT_MANAGE; customers create orders for and read their own bookings.
 */
public interface NexHomePaymentService {

	/** An order for the unpaid online bookings of the booking's checkout (the same open order again if unchanged). */
	NexHomePaymentOrderResponse createOrder(NexHomePaymentOrderCreateRequest request);

	/** Staff record that the order was paid (the MANUAL provider's capture). */
	NexHomeBookingPaymentsResponse capture(NexHomePaymentCaptureRequest request);

	/** A provider's webhook: stored once, signature checked, then processed (captured / failed payments). */
	NexHomeWebhookEventResponse receiveWebhook(String provider, String signature, String body);

	NexHomeBookingPaymentsResponse getBookingPayments(Long bookingId);

	NexHomePageResponse<NexHomePaymentResponse> getPayments(Integer page, Integer size, String status,
			Long customerProfileId, String search);

	NexHomePageResponse<NexHomePaymentOrderResponse> getOrders(Integer page, Integer size);

	NexHomePaymentStatsResponse getStats();

	NexHomeRefundResponse createRefund(NexHomeRefundCreateRequest request);

	NexHomePageResponse<NexHomeRefundResponse> getRefunds(Integer page, Integer size, String status);

	NexHomePageResponse<NexHomeCashCollectionResponse> getCashCollections(Integer page, Integer size, String status);

	NexHomeCashCollectionResponse updateCashCollection(NexHomeCashCollectionUpdateRequest request);

	NexHomeCashCollectionResponse acknowledgeCashCollection(NexHomeCashCollectionAcknowledgeRequest request);

	NexHomePageResponse<NexHomeWebhookEventResponse> getWebhookEvents(Integer page, Integer size);
}
