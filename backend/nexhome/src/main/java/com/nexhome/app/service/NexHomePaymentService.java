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
	NexHomeWebhookEventResponse receiveWebhook(String provider, java.util.Map<String, String> headers, String body);

	/** Asks the gateway whether the order was paid; PAID captures it (the order's customer or PAYMENT_MANAGE write). */
	com.nexhome.app.dto.NexHomePaymentOrderResponse verifyOrder(Long paymentOrderId);

	/** The hosted payment page (public HTML) for an order's checkout token, returning to the app's address. */
	String checkoutPage(String checkoutToken, String returnUrl);

	NexHomeBookingPaymentsResponse getBookingPayments(Long bookingId);

	NexHomePageResponse<NexHomePaymentResponse> getPayments(Integer page, Integer size, String status,
			Long customerProfileId, String search);

	NexHomePageResponse<NexHomePaymentOrderResponse> getOrders(Integer page, Integer size, String status,
			String search);

	/** One order with its bookings, every gateway attempt and the payments captured (PAYMENT_MANAGE read). */
	com.nexhome.app.dto.NexHomePaymentOrderDetailResponse getOrder(Long paymentOrderId);

	/**
	 * Staff: fetches the order's attempts from the gateway again (an open order is also checked and captured if
	 * paid, as verify does), then returns it as getOrder.
	 */
	com.nexhome.app.dto.NexHomePaymentOrderDetailResponse syncOrder(Long paymentOrderId);

	NexHomePaymentStatsResponse getStats();

	NexHomeRefundResponse createRefund(NexHomeRefundCreateRequest request);

	NexHomePageResponse<NexHomeRefundResponse> getRefunds(Integer page, Integer size, String status);

	NexHomePageResponse<NexHomeCashCollectionResponse> getCashCollections(Integer page, Integer size, String status,
			String search);

	NexHomeCashCollectionResponse updateCashCollection(NexHomeCashCollectionUpdateRequest request);

	NexHomeCashCollectionResponse acknowledgeCashCollection(NexHomeCashCollectionAcknowledgeRequest request);

	NexHomePageResponse<NexHomeWebhookEventResponse> getWebhookEvents(Integer page, Integer size);

	/**
	 * The scheduled job (V68): asks the gateway for the charges and settlement of recent paid orders that have none
	 * yet; returns how many have their charges. Nothing for MANUAL.
	 */
	int refreshGatewayCharges();

	/**
	 * Whether a booking's online payment has come in: an open gateway order of its checkout is checked (and captured
	 * if paid). The booking's customer, its assigned partner, or PAYMENT_MANAGE write.
	 */
	com.nexhome.app.dto.NexHomeBookingPaymentStatusResponse checkBookingPayment(Long bookingId);

	/**
	 * The online payment of a finished booking did not go through and the partner collected cash: open gateway orders
	 * are ended, the booking becomes CASH with its cash collected (PAID). A payment that came in meanwhile wins (the
	 * booking stays ONLINE, PAID). The assigned partner or PAYMENT_MANAGE write.
	 */
	com.nexhome.app.dto.NexHomeBookingPaymentStatusResponse collectCashInstead(
			com.nexhome.app.dto.NexHomeCollectCashRequest request);
}
