package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nexhome.app.config.NexHomeCashfreeProperties;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ServiceUnavailableException;

import tools.jackson.databind.json.JsonMapper;

/** What Cashfree's PG API receives and how its answers and webhooks are read, against a mock server. */
class NexHomeCashfreePaymentGatewayTest {

	private static final String BASE = "https://sandbox.cashfree.com/pg";
	private static final String SECRET = "test-secret-key";

	private MockRestServiceServer server;
	private NexHomeCashfreePaymentGateway gateway;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new NexHomeCashfreePaymentGateway(
				new NexHomeCashfreeProperties("TEST_APP", SECRET, "sandbox", null, null, 5), builder.build());
	}

	@Test
	void createsTheOrderWithTheCustomerAndReturnsTheSession() {
		server.expect(requestTo(BASE + "/orders"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-client-id", "TEST_APP"))
				.andExpect(header("x-client-secret", SECRET))
				.andExpect(header("x-api-version", "2025-01-01"))
				.andExpect(content().json("""
						{"order_id": "NHabc_1", "order_amount": 412.50, "order_currency": "INR",
						 "customer_details": {"customer_id": "NHCUST0001", "customer_phone": "9876543210",
						                      "customer_name": "Asha K"}}"""))
				.andRespond(withSuccess("""
						{"cf_order_id": "2149", "order_id": "NHabc_1", "order_status": "ACTIVE",
						 "payment_session_id": "session_abc123XYZ"}""", MediaType.APPLICATION_JSON));

		NexHomePaymentGateway.Order order = gateway.createOrder("NHabc_1", new BigDecimal("412.50"), "INR",
				new NexHomePaymentGateway.Customer("NHCUST0001", "9876543210", "Asha K", null));

		assertThat(order.providerOrderId()).isEqualTo("NHabc_1");
		assertThat(order.checkoutToken()).isEqualTo("session_abc123XYZ");
		assertThat(gateway.provider()).isEqualTo("CASHFREE");
		assertThat(gateway.checkoutMode()).isEqualTo("sandbox");
		server.verify();
	}

	@Test
	void aPaidOrderGivesItsSuccessfulPayment() {
		server.expect(requestTo(BASE + "/orders/NHabc_1"))
				.andRespond(withSuccess("{\"order_id\": \"NHabc_1\", \"order_status\": \"PAID\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/orders/NHabc_1/payments"))
				.andRespond(withSuccess("""
						[{"cf_payment_id": "5114911", "payment_status": "FAILED", "payment_group": "credit_card",
						  "payment_method": {"card": {"card_network": "visa", "card_number": "XXXXXXXXXXXX1111"}}},
						 {"cf_payment_id": "5114912", "payment_status": "SUCCESS", "payment_group": "upi",
						  "payment_amount": 412.5, "bank_reference": "1234567890",
						  "payment_method": {"upi": {"channel": "collect", "upi_id": "asha@okaxis"}}}]""",
						MediaType.APPLICATION_JSON));

		NexHomePaymentGateway.OrderStatus status = gateway.fetchOrder("NHabc_1");

		assertThat(status.status()).isEqualTo("PAID");
		assertThat(status.providerPaymentId()).isEqualTo("5114912");
		assertThat(status.paymentMode()).isEqualTo("upi");
		assertThat(status.attempts()).extracting(NexHomePaymentGateway.Attempt::methodDetail)
				.containsExactly("visa XXXXXXXXXXXX1111", "asha@okaxis");
		assertThat(status.attempts().get(1).amount()).isEqualByComparingTo("412.5");
		assertThat(status.attempts().get(1).bankReference()).isEqualTo("1234567890");
	}

	@Test
	void anUnpaidOrderGivesItsLatestAttempt() {
		server.expect(requestTo(BASE + "/orders/NHabc_1"))
				.andRespond(withSuccess("{\"order_status\": \"ACTIVE\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/orders/NHabc_1/payments"))
				.andRespond(withSuccess("""
						[{"cf_payment_id": "1", "payment_status": "USER_DROPPED", "payment_time": "2026-11-11T10:00:00+05:30"},
						 {"cf_payment_id": "2", "payment_status": "FAILED", "payment_time": "2026-11-11T10:05:00+05:30",
						  "payment_message": "Insufficient balance"}]""", MediaType.APPLICATION_JSON));

		NexHomePaymentGateway.OrderStatus status = gateway.fetchOrder("NHabc_1");

		assertThat(status.status()).isEqualTo("PENDING");
		assertThat(status.attemptStatus()).isEqualTo("FAILED");
		assertThat(status.attemptId()).isEqualTo("2");
		assertThat(status.attempts()).hasSize(2);
		assertThat(status.attempts().get(1).status()).isEqualTo("FAILED");
		assertThat(status.attempts().get(1).attemptedAt()).isNotNull();
		assertThat(status.failureMessage()).isEqualTo("Insufficient balance");
	}

	@Test
	void anActiveOrderIsPendingAndAnExpiredOneExpired() {
		server.expect(requestTo(BASE + "/orders/NHabc_1"))
				.andRespond(withSuccess("{\"order_status\": \"ACTIVE\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/orders/NHabc_1/payments"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		assertThat(gateway.fetchOrder("NHabc_1").status()).isEqualTo("PENDING");

		server.reset();
		server.expect(requestTo(BASE + "/orders/NHabc_2"))
				.andRespond(withSuccess("{\"order_status\": \"EXPIRED\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/orders/NHabc_2/payments"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		assertThat(gateway.fetchOrder("NHabc_2").status()).isEqualTo("EXPIRED");
	}

	@Test
	void refundsByOrderAndRefusesPayouts() {
		server.expect(requestTo(BASE + "/orders/NHabc_1/refunds"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"refund_amount\": 100, \"refund_id\": \"NHREF7\"}"))
				.andRespond(withSuccess("{\"cf_refund_id\": \"88001\", \"refund_status\": \"PENDING\"}",
						MediaType.APPLICATION_JSON));
		assertThat(gateway.refund("NHabc_1", "5114912", "NHREF7", new BigDecimal("100"))).isEqualTo("88001");
		assertThatThrownBy(() -> gateway.transfer("k", BigDecimal.TEN, "x"))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void aRefusalIsAClientSafeError() {
		server.expect(requestTo(BASE + "/orders")).andRespond(withBadRequest()
				.body("{\"message\": \"customer_phone is invalid\"}").contentType(MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> gateway.createOrder("NHabc_1", BigDecimal.TEN, "INR",
				new NexHomePaymentGateway.Customer("NHCUST0001", "123", null, null)))
				.isInstanceOf(ServiceUnavailableException.class)
				.hasMessage("The payment gateway could not create the payment; please try again.");
	}

	@Test
	void webhooksAreSignedOverTheTimestampAndBody() throws Exception {
		String body = """
				{"type": "PAYMENT_SUCCESS_WEBHOOK", "event_time": "2026-11-11T10:00:00+05:30",
				 "data": {"order": {"order_id": "NHabc_1", "order_amount": 412.5},
				          "payment": {"cf_payment_id": 5114912, "payment_status": "SUCCESS", "payment_group": "upi"}}}""";
		String timestamp = "1762835400000";
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String signature = Base64.getEncoder().encodeToString(mac.doFinal((timestamp + body).getBytes(StandardCharsets.UTF_8)));

		assertThat(gateway.verifyWebhook(body, Map.of("x-webhook-signature", signature, "x-webhook-timestamp", timestamp)))
				.isTrue();
		assertThat(gateway.verifyWebhook(body + " ", Map.of("x-webhook-signature", signature, "x-webhook-timestamp",
				timestamp))).isFalse();
		assertThat(gateway.verifyWebhook(body, Map.of())).isFalse();

		NexHomePaymentGateway.WebhookEvent event = gateway.parseWebhook(JsonMapper.builder().build().readTree(body));
		assertThat(event.type()).isEqualTo("payment.captured");
		assertThat(event.providerOrderId()).isEqualTo("NHabc_1");
		assertThat(event.providerPaymentId()).isEqualTo("5114912");
		assertThat(event.paymentMode()).isEqualTo("upi");
		assertThat(event.eventId()).isEqualTo("PAYMENT_SUCCESS_WEBHOOK:5114912");
	}

	@Test
	void readsTheChargesAndSettlementOfAnOrder() {
		server.expect(requestTo(BASE + "/orders/NHabc_1/settlements"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{"cf_payment_id": "5114912", "order_id": "NHabc_1", "entity": "settlement", "order_amount": 100,
						 "service_charge": 10, "service_tax": 1.8, "settlement_amount": 88.2, "cf_settlement_id": "6121238",
						 "transfer_time": "2026-11-12T12:57:52+05:30", "transfer_utr": "N87912312",
						 "settlement_currency": "INR"}""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/orders/NHabc_2/settlements"))
				.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
						.withResourceNotFound().body("{\"message\": \"settlement not found\"}")
						.contentType(MediaType.APPLICATION_JSON));

		NexHomePaymentGateway.Charges charges = gateway.fetchCharges("NHabc_1");
		assertThat(charges.providerPaymentId()).isEqualTo("5114912");
		assertThat(charges.serviceCharge()).isEqualByComparingTo("10");
		assertThat(charges.serviceTax()).isEqualByComparingTo("1.8");
		assertThat(charges.settlementAmount()).isEqualByComparingTo("88.2");
		assertThat(charges.settlementId()).isEqualTo("6121238");
		assertThat(charges.settlementUtr()).isEqualTo("N87912312");
		assertThat(charges.settledAt()).isEqualTo(java.time.Instant.parse("2026-11-12T07:27:52Z"));
		// Not settled yet: nothing, no error
		assertThat(gateway.fetchCharges("NHabc_2")).isNull();
		server.verify();
	}

	@Test
	void theChargesWebhookCarriesTheCharges() {
		NexHomePaymentGateway.WebhookEvent event = gateway.parseWebhook(JsonMapper.builder().build().readTree("""
				{"type": "PAYMENT_CHARGES_WEBHOOK", "event_time": "2026-11-12T08:00:05+05:30",
				 "data": {"order": {"order_id": "NHabc_1", "order_amount": 6.00},
				          "payment": {"cf_payment_id": 5979305158, "payment_status": "SUCCESS"},
				          "charges_details": {"service_charge": 0.12, "service_tax": 0.02, "settlement_amount": 5.86,
				                              "settlement_currency": "INR", "service_charge_discount": null}}}"""));
		assertThat(event.type()).isEqualTo("payment.charges");
		assertThat(event.eventId()).isEqualTo("PAYMENT_CHARGES_WEBHOOK:5979305158");
		assertThat(event.charges().providerPaymentId()).isEqualTo("5979305158");
		assertThat(event.charges().serviceCharge()).isEqualByComparingTo("0.12");
		assertThat(event.charges().serviceTax()).isEqualByComparingTo("0.02");
		assertThat(event.charges().settlementAmount()).isEqualByComparingTo("5.86");
		assertThat(event.charges().settlementId()).isNull();
	}

	@Test
	void theSurchargeTheCustomerPaidIsRead() {
		server.expect(requestTo(BASE + "/orders/NHabc_1"))
				.andRespond(withSuccess("{\"order_id\": \"NHabc_1\", \"order_status\": \"PAID\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/orders/NHabc_1/payments"))
				.andRespond(withSuccess("""
						[{"cf_payment_id": "5114912", "payment_status": "SUCCESS", "payment_amount": 408.47,
						  "payment_group": "upi", "payment_time": "2026-11-12T10:00:00+05:30",
						  "payment_surcharge": {"payment_surcharge_service_charge": 7.18,
						                        "payment_surcharge_service_tax": 1.29}}]""", MediaType.APPLICATION_JSON));

		NexHomePaymentGateway.Attempt paid = gateway.fetchOrder("NHabc_1").attempts().get(0);
		assertThat(paid.surcharge()).isEqualByComparingTo("7.18");
		assertThat(paid.surchargeTax()).isEqualByComparingTo("1.29");

		// The success webhook carries the payment as an attempt, surcharge included
		NexHomePaymentGateway.WebhookEvent event = gateway.parseWebhook(JsonMapper.builder().build().readTree("""
				{"type": "PAYMENT_SUCCESS_WEBHOOK", "data": {"order": {"order_id": "NHabc_1"},
				 "payment": {"cf_payment_id": 5114912, "payment_status": "SUCCESS", "payment_amount": 408.47,
				             "payment_surcharge": {"payment_surcharge_service_charge": 7.18,
				                                   "payment_surcharge_service_tax": 1.29}}}}"""));
		assertThat(event.attempt().providerPaymentId()).isEqualTo("5114912");
		assertThat(event.attempt().surcharge()).isEqualByComparingTo("7.18");
		assertThat(event.attempt().surchargeTax()).isEqualByComparingTo("1.29");
		server.verify();
	}

	@Test
	void endsAnOrderWithPatchTerminated() {
		server.expect(requestTo(BASE + "/orders/NHabc_1"))
				.andExpect(method(HttpMethod.PATCH))
				.andExpect(header("x-client-secret", SECRET))
				.andExpect(content().json("{\"order_status\": \"TERMINATED\"}"))
				.andRespond(withSuccess("{\"order_id\": \"NHabc_1\", \"order_status\": \"TERMINATED\"}",
						MediaType.APPLICATION_JSON));

		gateway.cancelOrder("NHabc_1");
		server.verify();
	}

	@Test
	void aRefusedEndIsAServiceUnavailable() {
		// Cashfree refuses to end an order while one of its payments is still pending at the bank
		server.expect(requestTo(BASE + "/orders/NHabc_1"))
				.andExpect(method(HttpMethod.PATCH))
				.andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
						.body("{\"message\": \"order cannot be terminated\", \"code\": \"order_terminate_failed\"}"));

		assertThatThrownBy(() -> gateway.cancelOrder("NHabc_1"))
				.isInstanceOf(ServiceUnavailableException.class)
				.hasMessage("The payment gateway could not cancel the online payment; please try again.");
		server.verify();
	}

	@Test
	void theRealConnectionCanSendPatch() throws Exception {
		// The mock server above skips the connection; HttpURLConnection (SimpleClientHttpRequestFactory) threw
		// "Invalid HTTP method: PATCH" here, so ending an order never reached Cashfree. Creating the request opens
		// nothing.
		var request = NexHomeCashfreePaymentGateway.timeouts(5)
				.createRequest(java.net.URI.create(BASE + "/orders/NHabc_1"), HttpMethod.PATCH);
		assertThat(request.getMethod()).isEqualTo(HttpMethod.PATCH);
	}

	@Test
	void keysAreRequired() {
		assertThatThrownBy(() -> new NexHomeCashfreePaymentGateway(
				new NexHomeCashfreeProperties("", "", "sandbox", null, null, 5), RestClient.create()))
				.isInstanceOf(IllegalStateException.class);
	}
}
