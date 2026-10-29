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

	/** Public: the provider signs the body (header X-NexHome-Signature: hex HMAC-SHA256). */
	@PostMapping("/webhooks/{provider}")
	public NexHomeWebhookEventResponse receiveWebhook(@PathVariable String provider,
			@RequestHeader(name = "X-NexHome-Signature", required = false) String signature,
			@RequestBody String body) {
		return paymentService.receiveWebhook(provider, signature, body);
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
			@RequestParam(required = false) Integer size) {
		return paymentService.getOrders(page, size);
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
			@RequestParam(required = false) String status) {
		return paymentService.getCashCollections(page, size, status);
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
