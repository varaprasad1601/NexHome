package com.nexhome.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
import com.nexhome.app.service.NexHomePaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Module 7, money in: payment orders, captured payments, refunds, cash collections and providers' webhooks. Staff
 * endpoints need PAYMENT_MANAGE; customers and partners act on their own bookings (checked in the service).
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class NexHomePaymentController {

	private final NexHomePaymentService paymentService;

	@PostMapping("/orders/create")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomePaymentOrderResponse createOrder(@Valid @RequestBody NexHomePaymentOrderCreateRequest request) {
		return paymentService.createOrder(request);
	}

	@PostMapping("/orders/capture")
	@PreAuthorize("@permissions.can('PAYMENT_MANAGE', 'WRITE')")
	public NexHomeBookingPaymentsResponse capture(@Valid @RequestBody NexHomePaymentCaptureRequest request) {
		return paymentService.capture(request);
	}

	/**
	 * Public: the provider signs the body (MANUAL: X-NexHome-Signature, hex HMAC-SHA256; CASHFREE:
	 * x-webhook-signature over x-webhook-timestamp + body).
	 */
	@PostMapping("/webhooks/{provider}")
	public NexHomeWebhookEventResponse receiveWebhook(@PathVariable String provider,
			@RequestHeader java.util.Map<String, String> headers, @RequestBody String body) {
		java.util.Map<String, String> lower = new java.util.HashMap<>();
		headers.forEach((name, value) -> lower.put(name.toLowerCase(java.util.Locale.ROOT), value));
		return paymentService.receiveWebhook(provider, lower, body);
	}

	/** Staff: one order with its bookings, every gateway attempt (success, failed, pending, ...) and payments. */
	@GetMapping("/orders/{paymentOrderId}")
	@org.springframework.security.access.prepost.PreAuthorize("@permissions.can('PAYMENT_MANAGE', 'READ')")
	public com.nexhome.app.dto.NexHomePaymentOrderDetailResponse getOrder(@PathVariable Long paymentOrderId) {
		return paymentService.getOrder(paymentOrderId);
	}

	/** Staff: fetch the order's attempts from the gateway again (and capture it if it was paid). */
	@PostMapping("/orders/sync")
	@org.springframework.security.access.prepost.PreAuthorize("@permissions.can('PAYMENT_MANAGE', 'WRITE')")
	public com.nexhome.app.dto.NexHomePaymentOrderDetailResponse syncOrder(
			@Valid @RequestBody com.nexhome.app.dto.NexHomePaymentOrderVerifyRequest request) {
		return paymentService.syncOrder(request.paymentOrderId());
	}

	/** The customer (or staff): asks the gateway whether the order was paid, and captures it if so. */
	@PostMapping("/orders/verify")
	public NexHomePaymentOrderResponse verifyOrder(
			@Valid @RequestBody com.nexhome.app.dto.NexHomePaymentOrderVerifyRequest request) {
		return paymentService.verifyOrder(request.paymentOrderId());
	}

	/** Public: the hosted payment page the customer app opens (Cashfree checkout), then back to the app. */
	@GetMapping(value = "/checkout", produces = org.springframework.http.MediaType.TEXT_HTML_VALUE)
	public String checkoutPage(@org.springframework.web.bind.annotation.RequestParam String session,
			@org.springframework.web.bind.annotation.RequestParam("return") String returnUrl) {
		return paymentService.checkoutPage(session, returnUrl);
	}

	/** The booking's customer, its partner or staff: has its online payment come in? (asks the gateway) */
	@PostMapping("/bookings/check")
	public com.nexhome.app.dto.NexHomeBookingPaymentStatusResponse checkBookingPayment(
			@Valid @RequestBody com.nexhome.app.dto.NexHomeBookingPaymentCheckRequest request) {
		return paymentService.checkBookingPayment(request.bookingId());
	}

	/** The partner (or staff): the online payment did not go through, so the partner collected cash instead. */
	@PostMapping("/bookings/collect-cash")
	public com.nexhome.app.dto.NexHomeBookingPaymentStatusResponse collectCashInstead(
			@Valid @RequestBody com.nexhome.app.dto.NexHomeCollectCashRequest request) {
		return paymentService.collectCashInstead(request);
	}

	@GetMapping("/bookings/{bookingId}")
	public NexHomeBookingPaymentsResponse getBookingPayments(@PathVariable Long bookingId) {
		return paymentService.getBookingPayments(bookingId);
	}

	@GetMapping
	@PreAuthorize("@permissions.can('PAYMENT_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomePaymentResponse> getPayments(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long customerProfileId, @RequestParam(required = false) String search) {
		return paymentService.getPayments(page, size, status, customerProfileId, search);
	}

	@GetMapping("/orders")
	@PreAuthorize("@permissions.can('PAYMENT_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomePaymentOrderResponse> getOrders(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String status,
			@RequestParam(required = false) String search) {
		return paymentService.getOrders(page, size, status, search);
	}

	@GetMapping("/stats")
	@PreAuthorize("@permissions.can('PAYMENT_MANAGE', 'READ')")
	public NexHomePaymentStatsResponse getStats() {
		return paymentService.getStats();
	}

	@PostMapping("/refunds/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('PAYMENT_MANAGE', 'WRITE')")
	public NexHomeRefundResponse createRefund(@Valid @RequestBody NexHomeRefundCreateRequest request) {
		return paymentService.createRefund(request);
	}

	@GetMapping("/refunds")
	@PreAuthorize("@permissions.can('PAYMENT_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeRefundResponse> getRefunds(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String status) {
		return paymentService.getRefunds(page, size, status);
	}

	@GetMapping("/cash-collections")
	@PreAuthorize("@permissions.can('PAYMENT_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeCashCollectionResponse> getCashCollections(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String status, @RequestParam(required = false) String search) {
		return paymentService.getCashCollections(page, size, status, search);
	}

	@PutMapping("/cash-collections/update")
	public NexHomeCashCollectionResponse updateCashCollection(
			@Valid @RequestBody NexHomeCashCollectionUpdateRequest request) {
		return paymentService.updateCashCollection(request);
	}

	@PutMapping("/cash-collections/acknowledge")
	public NexHomeCashCollectionResponse acknowledgeCashCollection(
			@Valid @RequestBody NexHomeCashCollectionAcknowledgeRequest request) {
		return paymentService.acknowledgeCashCollection(request);
	}

	@GetMapping("/webhooks")
	@PreAuthorize("@permissions.can('PAYMENT_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeWebhookEventResponse> getWebhookEvents(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return paymentService.getWebhookEvents(page, size);
	}
}
