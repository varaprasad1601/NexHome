package com.nexhome.app.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.nexhome.app.config.NexHomeCashfreeProperties;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ServiceUnavailableException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * Cashfree Payment Gateway ({@code nexhome.payments.provider=cashfree}, provider CASHFREE): the customer pays in the
 * app through Cashfree's checkout. Orders: {@code POST /orders} (our reference as {@code order_id}, the customer's
 * id and 10-digit phone) answers a {@code payment_session_id}, which the hosted payment page opens. Status:
 * {@code GET /orders/{id}} (PAID) and {@code /orders/{id}/payments} (the SUCCESS payment's {@code cf_payment_id} and
 * {@code payment_group}). Charges: the PAYMENT_CHARGES_WEBHOOK and {@code GET /orders/{id}/settlements}. Refunds: {@code POST /orders/{id}/refunds}. Webhooks: Base64 HMAC-SHA256 of
 * x-webhook-timestamp + the raw body with the secret key (x-webhook-signature). Partner payouts are made outside the
 * app, so transfers are refused. Neither the secret key nor card details are ever logged.
 */
@Component
@ConditionalOnProperty(name = "nexhome.payments.provider", havingValue = "cashfree")
@Slf4j
public class NexHomeCashfreePaymentGateway implements NexHomePaymentGateway {

	public static final String PROVIDER = "CASHFREE";

	private final NexHomeCashfreeProperties settings;
	private final RestClient restClient;

	@Autowired
	public NexHomeCashfreePaymentGateway(NexHomeCashfreeProperties settings) {
		this(settings, RestClient.builder().requestFactory(timeouts(settings.timeout())).build());
	}

	/** With a ready client (tests bind it to a mock server). */
	NexHomeCashfreePaymentGateway(NexHomeCashfreeProperties settings, RestClient restClient) {
		if (blank(settings.appId()) || blank(settings.secretKey())) {
			throw new IllegalStateException("nexhome.payments.provider=cashfree needs CASHFREE_APP_ID and "
					+ "CASHFREE_SECRET_KEY.");
		}
		this.settings = settings;
		this.restClient = restClient;
		log.info("Payments go through Cashfree ({})", settings.production() ? "production" : "sandbox");
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public Order createOrder(String reference, BigDecimal amount, String currency, Customer customer) {
		Map<String, Object> customerDetails = new LinkedHashMap<>();
		customerDetails.put("customer_id", customer.customerId());
		customerDetails.put("customer_phone", customer.phone());
		if (!blank(customer.name())) {
			customerDetails.put("customer_name", customer.name());
		}
		if (!blank(customer.email())) {
			customerDetails.put("customer_email", customer.email());
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("order_id", reference);
		body.put("order_amount", amount);
		body.put("order_currency", currency);
		body.put("customer_details", customerDetails);
		if (!blank(settings.notifyUrl())) {
			body.put("order_meta", Map.of("notify_url", settings.notifyUrl().trim()));
		}
		JsonNode answer = call("create the payment", () -> restClient.post()
				.uri(settings.baseUrl() + "/orders")
				.headers(this::authHeaders)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(JsonNode.class));
		String session = text(answer, "payment_session_id");
		if (session == null) {
			throw new ServiceUnavailableException("The payment gateway did not open the payment; please try again.");
		}
		return new Order(text(answer, "order_id") == null ? reference : text(answer, "order_id"), session);
	}

	@Override
	public OrderStatus fetchOrder(String providerOrderId) {
		JsonNode order = call("check the payment", () -> restClient.get()
				.uri(settings.baseUrl() + "/orders/{id}", providerOrderId)
				.headers(this::authHeaders)
				.retrieve()
				.body(JsonNode.class));
		String status = text(order, "order_status");
		JsonNode payments = call("check the payment", () -> restClient.get()
				.uri(settings.baseUrl() + "/orders/{id}/payments", providerOrderId)
				.headers(this::authHeaders)
				.retrieve()
				.body(JsonNode.class));
		JsonNode success = null;
		JsonNode latest = null;
		List<Attempt> attempts = new java.util.ArrayList<>();
		if (payments != null && payments.isArray()) {
			for (JsonNode payment : payments) {
				if (text(payment, "cf_payment_id") != null) {
					attempts.add(attemptOf(payment));
				}
				// The most recent attempt (by payment_completion_time / payment_time, else the list's first)
				if (latest == null || newer(payment, latest)) {
					latest = payment;
				}
				if ("SUCCESS".equals(text(payment, "payment_status"))) {
					success = payment;
				}
			}
		}
		if ("PAID".equals(status) && success != null) {
			return new OrderStatus(OrderStatus.PAID, text(success, "cf_payment_id"), text(success, "payment_group"),
					null, "SUCCESS", text(success, "cf_payment_id"), attempts);
		}
		String message = latest == null ? null : messageOf(latest);
		String latestStatus = latest == null ? null : text(latest, "payment_status");
		String latestId = latest == null ? null : text(latest, "cf_payment_id");
		if ("EXPIRED".equals(status) || "TERMINATED".equals(status)) {
			return new OrderStatus(OrderStatus.EXPIRED, null, null, "The payment window has closed.", latestStatus,
					latestId, attempts);
		}
		return new OrderStatus(OrderStatus.PENDING, null, null, message, latestStatus, latestId, attempts);
	}

	/** PATCH /orders/{id} with order_status TERMINATED: the customer can no longer pay it. */
	@Override
	public void cancelOrder(String providerOrderId) {
		call("cancel the online payment", () -> restClient.patch()
				.uri(settings.baseUrl() + "/orders/{id}", providerOrderId)
				.headers(this::authHeaders)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("order_status", "TERMINATED"))
				.retrieve()
				.body(JsonNode.class));
	}

	/**
	 * GET /orders/{id}/settlements: the charge, its tax and what Cashfree pays NexHome, with the settlement's id,
	 * UTR and transfer time. Before the order is settled Cashfree has nothing (404 or an empty answer): null.
	 */
	@Override
	public Charges fetchCharges(String providerOrderId) {
		JsonNode answer;
		try {
			answer = restClient.get()
					.uri(settings.baseUrl() + "/orders/{id}/settlements", providerOrderId)
					.headers(this::authHeaders)
					.retrieve()
					.body(JsonNode.class);
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() != 404) {
				log.warn("Cashfree did not give the settlement of order {}: HTTP {}", providerOrderId,
						ex.getStatusCode().value());
			}
			return null;
		}
		catch (RestClientException ex) {
			log.warn("Cashfree could not be reached for the settlement of order {}: {}", providerOrderId,
					ex.getMessage());
			return null;
		}
		JsonNode settlement = answer != null && answer.isArray() ? (answer.isEmpty() ? null : answer.get(0)) : answer;
		if (settlement == null || !settlement.isObject()) {
			return null;
		}
		BigDecimal charge = decimal(settlement, "service_charge");
		BigDecimal tax = decimal(settlement, "service_tax");
		BigDecimal settled = decimal(settlement, "settlement_amount");
		if (charge == null && tax == null && settled == null) {
			return null;
		}
		String settlementId = text(settlement, "cf_settlement_id");
		return new Charges(text(settlement, "cf_payment_id"), charge, tax, settled,
				text(settlement, "settlement_currency"),
				settlementId != null ? settlementId : text(settlement, "settlement_id"),
				text(settlement, "transfer_utr"), instantOf(text(settlement, "transfer_time")));
	}

	/** A number field (Cashfree sends numbers, sometimes as text); null when missing or unreadable. */
	private static BigDecimal decimal(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.path(field);
		if (value == null || !(value.isNumber() || value.isString())) {
			return null;
		}
		try {
			return new BigDecimal(value.asString().trim());
		}
		catch (NumberFormatException ignored) {
			return null;
		}
	}

	/** One of Cashfree's payments of an order (GET /orders/{id}/payments) as an attempt. */
	private static Attempt attemptOf(JsonNode payment) {
		JsonNode error = payment.path("error_details");
		BigDecimal amount = decimal(payment, "payment_amount");
		// The surcharge model (V69): Cashfree's charge and GST the customer paid on top
		JsonNode surcharge = payment.path("payment_surcharge");
		String currency = text(payment, "payment_currency");
		return new Attempt(text(payment, "cf_payment_id"), text(payment, "payment_status"),
				text(payment, "payment_group"), methodOf(payment.path("payment_method")), amount,
				currency == null || currency.length() != 3 ? "INR" : currency, text(payment, "bank_reference"),
				text(error, "error_code"), messageOf(payment), instantOf(text(payment, "payment_time")),
				instantOf(text(payment, "payment_completion_time")),
				decimal(surcharge, "payment_surcharge_service_charge"), decimal(surcharge, "payment_surcharge_service_tax"));
	}

	/** The payment's message, or its error's description when that says more. */
	private static String messageOf(JsonNode payment) {
		String message = text(payment, "payment_message");
		String reason = text(payment.path("error_details"), "error_description");
		return reason != null && (message == null || message.length() < 8) ? reason : message;
	}

	/** What was used, from Cashfree's payment_method: the UPI id, card network and number, bank or wallet. */
	private static String methodOf(JsonNode method) {
		if (method == null || !method.isObject()) {
			return null;
		}
		JsonNode upi = method.path("upi");
		if (upi.isObject()) {
			String id = text(upi, "upi_id");
			return id != null ? id : text(upi, "channel") == null ? "UPI" : "UPI " + text(upi, "channel");
		}
		JsonNode card = method.path("card");
		if (card.isObject()) {
			String detail = String.join(" ", java.util.stream.Stream.of(text(card, "card_network"),
					text(card, "card_type"), text(card, "card_number")).filter(java.util.Objects::nonNull).toList());
			return detail.isBlank() ? "Card" : detail;
		}
		JsonNode bank = method.path("netbanking");
		if (bank.isObject()) {
			String name = text(bank, "netbanking_bank_name");
			return name != null ? name : text(bank, "netbanking_bank_code");
		}
		JsonNode app = method.path("app");
		if (app.isObject()) {
			return text(app, "provider");
		}
		java.util.Iterator<String> names = method.propertyNames().iterator();
		return names.hasNext() ? names.next() : null;
	}

	private static java.time.Instant instantOf(String time) {
		if (time == null) {
			return null;
		}
		try {
			return java.time.OffsetDateTime.parse(time).toInstant();
		} catch (java.time.format.DateTimeParseException ex) {
			return null;
		}
	}

	@Override
	public String refund(String providerOrderId, String providerPaymentId, String reference, BigDecimal amount) {
		Map<String, Object> body = Map.of("refund_amount", amount, "refund_id", reference, "refund_note",
				"NexHome refund");
		JsonNode answer = call("refund the payment", () -> restClient.post()
				.uri(settings.baseUrl() + "/orders/{id}/refunds", providerOrderId)
				.headers(this::authHeaders)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(JsonNode.class));
		String id = text(answer, "cf_refund_id");
		return id == null ? reference : id;
	}

	@Override
	public Map<String, Object> transfer(String idempotencyKey, BigDecimal amount, String accountReference) {
		throw new InvalidRequestException("Partner payouts are made outside the app; record them under Partner Payouts.");
	}

	@Override
	public boolean verifyWebhook(String payload, Map<String, String> headers) {
		String signature = headers.get("x-webhook-signature");
		String timestamp = headers.get("x-webhook-timestamp");
		if (payload == null || blank(signature) || blank(timestamp)) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(settings.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] expected = Base64.getEncoder()
					.encode(mac.doFinal((timestamp.trim() + payload).getBytes(StandardCharsets.UTF_8)));
			// Constant time, so the comparison gives nothing away
			return MessageDigest.isEqual(expected, signature.trim().getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HMAC-SHA256 is not available", e);
		}
	}

	@Override
	public WebhookEvent parseWebhook(JsonNode body) {
		String type = text(body, "type");
		JsonNode data = body.path("data");
		JsonNode payment = data.path("payment");
		String paymentId = text(payment, "cf_payment_id");
		String mapped = switch (type == null ? "" : type) {
			case "PAYMENT_SUCCESS_WEBHOOK", "PAYMENT_SUCCESS" -> "payment.captured";
			case "PAYMENT_FAILED_WEBHOOK", "PAYMENT_FAILED" -> "payment.failed";
			// The charges Cashfree took, once it has worked them out (subscribe to "Success Payment TDR")
			case "PAYMENT_CHARGES_WEBHOOK" -> "payment.charges";
			default -> type;
		};
		JsonNode error = payment.path("error_details");
		JsonNode details = data.path("charges_details");
		Charges charges = details.isObject() ? new Charges(paymentId, decimal(details, "service_charge"),
				decimal(details, "service_tax"), decimal(details, "settlement_amount"),
				text(details, "settlement_currency"), null, null, null) : null;
		return new WebhookEvent(type == null ? null : type + ":" + (paymentId == null ? text(body, "event_time")
				: paymentId), mapped, text(data.path("order"), "order_id"), paymentId, text(payment, "payment_group"),
				text(error, "error_code"), text(error, "error_description"), charges,
				paymentId != null && text(payment, "payment_status") != null ? attemptOf(payment) : null);
	}

	@Override
	public String checkoutMode() {
		return settings.production() ? "production" : "sandbox";
	}

	// ------------------------------------------------------------------------------------------------------------

	private void authHeaders(org.springframework.http.HttpHeaders headers) {
		headers.set("x-client-id", settings.appId().trim());
		headers.set("x-client-secret", settings.secretKey().trim());
		headers.set("x-api-version", settings.version());
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
	}

	/** One call to Cashfree; its refusals and outages become a client-safe 503 (the reason is logged, no secrets). */
	private static JsonNode call(String what, java.util.function.Supplier<JsonNode> request) {
		try {
			return request.get();
		}
		catch (RestClientResponseException ex) {
			log.error("Cashfree refused to {}: HTTP {} {}", what, ex.getStatusCode().value(),
					ex.getResponseBodyAsString());
			throw new ServiceUnavailableException("The payment gateway could not " + what + "; please try again.");
		}
		catch (RestClientException ex) {
			log.error("Cashfree could not be reached to {}: {}", what, ex.getMessage());
			throw new ServiceUnavailableException("The payment gateway could not " + what + "; please try again.");
		}
	}

	private static String text(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		String text = value.asString();
		return text == null || text.isBlank() ? null : text.trim();
	}

	/** Whether attempt {@code a} is later than {@code b} (Cashfree's ISO times compare as text). */
	private static boolean newer(JsonNode a, JsonNode b) {
		String timeA = text(a, "payment_time");
		String timeB = text(b, "payment_time");
		return timeA != null && timeB != null && timeA.compareTo(timeB) > 0;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * Connect and read timeouts; buffered, so the JSON goes with a Content-Length (not chunked). The JDK's
	 * {@code java.net.http} client, not {@code HttpURLConnection} (SimpleClientHttpRequestFactory), which cannot send
	 * PATCH: ending an order ({@link #cancelOrder}) failed with "Invalid HTTP method: PATCH" before reaching Cashfree.
	 */
	static org.springframework.http.client.BufferingClientHttpRequestFactory timeouts(int seconds) {
		java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
				.version(java.net.http.HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(seconds))
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(Duration.ofSeconds(seconds));
		return new org.springframework.http.client.BufferingClientHttpRequestFactory(factory);
	}
}
