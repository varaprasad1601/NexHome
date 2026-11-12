package com.nexhome.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.config.NexHomeSecurityProperties;
import com.nexhome.app.mapper.NexHomeAuthRefreshTokenMapper;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAuthRefreshTokenRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Module 7 end to end against the dev database (rolled back): an online booking paid (order, recorded payment, or a
 * signed webhook), commission rules and the partner's payout, cash collected for a cash booking, and refunds. Staff
 * calls use a super user, so the dev database's grants do not matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class NexHomePaymentFlowTest {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	/** The dev default of nexhome.payments.webhook-secret. */
	private static final String WEBHOOK_SECRET = "nexhome-dev-webhook-secret";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JwtEncoder jwtEncoder;
	@Autowired
	private NexHomeSecurityProperties securityProperties;
	@Autowired
	private NexHomeRoleRepository roleRepository;
	@Autowired
	private NexHomeUserRepository userRepository;
	@Autowired
	private NexHomeUserRoleRepository userRoleRepository;
	@Autowired
	private NexHomeUserMapper userMapper;
	@Autowired
	private NexHomeAuthRefreshTokenRepository refreshTokenRepository;
	@Autowired
	private NexHomeAuthRefreshTokenMapper refreshTokenMapper;
	@Autowired
	private EntityManager entityManager;

	private String staff;
	private String customer;
	private String partner;
	private long customerId;
	private long addressId;
	private long partnerId;
	private long categoryId;
	private long serviceId;
	private LocalDate monday;

	@Test
	void anOnlineBookingIsPaidCompletedAndThePartnerPaidOut() throws Exception {
		setUp();
		long bookingId = book("ONLINE", 10);
		// No online payment fee any more (V68): the customer pays the price
		perform(get("/api/bookings/" + bookingId), customer, null)
				.andExpect(jsonPath("$.booking.transactionFeeAmount").value(0))
				.andExpect(jsonPath("$.booking.totalAmount").value(400));
		// The customer starts paying; asking again reuses the open order
		JsonNode order = json(perform(post("/api/payments/orders/create"), customer,
				"{\"bookingId\": %d}".formatted(bookingId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.amount").value(400))
				.andExpect(jsonPath("$.paymentStatus").value("CREATED"))
				.andExpect(jsonPath("$.provider").value("MANUAL")));
		long orderId = order.get("paymentOrderId").asLong();
		perform(post("/api/payments/orders/create"), customer, "{\"bookingId\": %d}".formatted(bookingId))
				.andExpect(jsonPath("$.paymentOrderId").value(orderId));
		// Staff record the payment: the booking is paid and confirmed
		perform(post("/api/payments/orders/capture"), customer, "{\"paymentOrderId\": %d}".formatted(orderId))
				.andExpect(status().isForbidden());
		long paymentId = json(perform(post("/api/payments/orders/capture"), staff,
				"{\"paymentOrderId\": %d, \"providerPaymentId\": \"UPI123%d\"}".formatted(orderId, orderId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payments[0].status").value("CAPTURED"))
				.andExpect(jsonPath("$.orders[0].paymentStatus").value("PAID"))).get("payments").get(0)
				.get("paymentId").asLong();
		perform(get("/api/bookings/" + bookingId), staff, null)
				.andExpect(jsonPath("$.booking.bookingStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.booking.paymentStatus").value("PAID"))
				.andExpect(jsonPath("$.history[1].reason").value(org.hamcrest.Matchers.startsWith("Payment received")));
		// Payment orders are found by the booking id, the order id or the customer, with their bookings and attempt
		String bookingPublicId = json(perform(get("/api/bookings/" + bookingId), staff, null)).get("booking")
				.get("publicId").asString();
		perform(get("/api/payments/orders?status=PAID&search=" + bookingPublicId), staff, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].paymentOrderId").value(orderId))
				.andExpect(jsonPath("$.content[0].bookings[0].publicId").value(bookingPublicId));
		perform(get("/api/payments/orders?search=" + order.get("publicId").asString()), staff, null)
				.andExpect(jsonPath("$.content[0].paymentOrderId").value(orderId));
		perform(get("/api/payments/orders?status=CREATED&search=" + bookingPublicId), staff, null)
				.andExpect(jsonPath("$.totalElements").value(0));
		perform(get("/api/payments/orders/" + orderId), staff, null)
				.andExpect(jsonPath("$.attempts[0].attemptStatus").value("SUCCESS"))
				.andExpect(jsonPath("$.attempts[0].providerPaymentId").value("UPI123" + orderId))
				.andExpect(jsonPath("$.payments[0].paymentId").value(paymentId));
		perform(get("/api/payments/cash-collections?search=" + bookingPublicId), staff, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
		perform(get("/api/payments/cash-collections?status=DUE&search=nobody-matches-this"), staff, null)
				.andExpect(jsonPath("$.totalElements").value(0));

		// Done: the partner is owed 400 less the service's 15% platform commission
		finish(bookingId);
		JsonNode payout = json(perform(get("/api/payments/bookings/" + bookingId), staff, null)
				.andExpect(jsonPath("$.payout.grossAmount").value(400))
				.andExpect(jsonPath("$.payout.platformCommission").value(60))
				.andExpect(jsonPath("$.payout.netAmount").value(340))
				.andExpect(jsonPath("$.payout.payoutStatus").value("PENDING"))).get("payout");
		long payoutId = payout.get("payoutId").asLong();
		perform(get("/api/payments/bookings/" + bookingId), customer, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payout").doesNotExist());

		// A bonus or a deduction changes the partner's share (partners are paid outside the app, V62)
		perform(put("/api/payouts/adjust"), staff, "{\"payoutId\": %d, \"adjustmentsAmount\": 25}".formatted(payoutId))
				.andExpect(jsonPath("$.netAmount").value(365));
		perform(put("/api/payouts/adjust"), staff, "{\"payoutId\": %d, \"adjustmentsAmount\": -400}".formatted(payoutId))
				.andExpect(status().isBadRequest());
		perform(get("/api/payouts/me"), partner, null)
				.andExpect(jsonPath("$.content[0].payoutId").value(payoutId));
		perform(get("/api/payments/stats"), staff, null).andExpect(jsonPath("$.capturedCount").isNumber())
				.andExpect(jsonPath("$.revenueAmount").isNumber())
				.andExpect(jsonPath("$.revenueMonths.length()").value(10));
	}

	@Test
	void refundsNeverExceedTheCapturedAmountAndCancellingRefunds() throws Exception {
		setUp();
		long bookingId = book("ONLINE", 10);
		long orderId = json(perform(post("/api/payments/orders/create"), customer,
				"{\"bookingId\": %d}".formatted(bookingId))).get("paymentOrderId").asLong();
		long paymentId = json(perform(post("/api/payments/orders/capture"), staff,
				"{\"paymentOrderId\": %d}".formatted(orderId))).get("payments").get(0).get("paymentId").asLong();

		perform(post("/api/payments/refunds/create"), staff,
				"{\"paymentId\": %d, \"amount\": 100, \"reason\": \"Goodwill\"}".formatted(paymentId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"));
		perform(post("/api/payments/refunds/create"), staff,
				"{\"paymentId\": %d, \"amount\": 301, \"reason\": \"Too much\"}".formatted(paymentId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"Refunds cannot exceed the captured amount; 300.00 can still be refunded."));
		perform(get("/api/bookings/" + bookingId), customer, null)
				.andExpect(jsonPath("$.booking.paymentStatus").value("PARTIALLY_REFUNDED"));
		// Cancelling refunds the rest
		perform(put("/api/bookings/cancel"), customer,
				"{\"bookingId\": %d, \"reason\": \"Not needed\"}".formatted(bookingId))
				.andExpect(status().isOk())
				// A cancelled booking has no payment status (V42); the payment shows the refund
				.andExpect(jsonPath("$.paymentStatus").doesNotExist());
		perform(get("/api/payments/bookings/" + bookingId), customer, null)
				.andExpect(jsonPath("$.payments[0].status").value("REFUNDED"))
				.andExpect(jsonPath("$.refunds.length()").value(2))
				.andExpect(jsonPath("$.refunds[0].amount").value(300))
				.andExpect(jsonPath("$.payments[0].refundedAmount").value(400));
	}

	/**
	 * An online job's Finish work asks the customer (V70): "not yet" keeps the work running, "done" stops it and
	 * completes the booking with its payment pending, which the customer then pays.
	 */
	@Test
	void theCustomerConfirmsAnOnlineJobIsDone() throws Exception {
		setUp();
		long bookingId = book("ONLINE", 11);
		String body = "{\"bookingId\": %d}".formatted(bookingId);
		perform(put("/api/bookings/status/update"), staff,
				"{\"bookingId\": %d, \"bookingStatus\": \"CONFIRMED\"}".formatted(bookingId));
		perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d, \"direct\": true}".formatted(bookingId, partnerId))
				.andExpect(status().isCreated());
		startWithCode(partner, customer, bookingId);

		// Nothing to confirm yet; only the partner (or staff) asks
		perform(put("/api/bookings/completion/respond"), customer,
				"{\"bookingId\": %d, \"completed\": true}".formatted(bookingId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Your professional has not asked you to confirm this job."));
		perform(put("/api/bookings/completion/request"), customer, body).andExpect(status().isForbidden());

		perform(put("/api/bookings/completion/request"), partner, body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.booking.bookingStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.booking.completionRequestedAt").isNotEmpty());
		// Not yet: the request goes, the work carries on
		perform(put("/api/bookings/completion/respond"), customer,
				"{\"bookingId\": %d, \"completed\": false}".formatted(bookingId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.booking.bookingStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.booking.completionRequestedAt").doesNotExist());
		// Asked again; the partner cannot answer for the customer
		perform(put("/api/bookings/completion/request"), partner, body).andExpect(status().isOk());
		perform(put("/api/bookings/completion/respond"), partner,
				"{\"bookingId\": %d, \"completed\": true}".formatted(bookingId))
				.andExpect(status().isForbidden());
		// Done: the work stops (work finished), the booking stays IN_PROGRESS with the payment pending
		perform(put("/api/bookings/completion/respond"), customer,
				"{\"bookingId\": %d, \"completed\": true}".formatted(bookingId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.booking.bookingStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.booking.paymentStatus").value("PENDING"))
				.andExpect(jsonPath("$.booking.actualEndAt").isNotEmpty())
				.andExpect(jsonPath("$.booking.completionConfirmedAt").isNotEmpty())
				.andExpect(jsonPath("$.history[?(@.reason == 'Customer confirmed the work is done')]").exists())
				.andExpect(jsonPath("$.history[?(@.reason == 'Work finished')]").exists());
		// The customer pays now: the payment completes the booking
		long orderId = json(perform(post("/api/payments/orders/create"), customer, body)
				.andExpect(status().isCreated())).get("paymentOrderId").asLong();
		perform(post("/api/payments/orders/capture"), staff, "{\"paymentOrderId\": %d}".formatted(orderId))
				.andExpect(status().isOk());
		perform(get("/api/bookings/" + bookingId), customer, null)
				.andExpect(jsonPath("$.booking.paymentStatus").value("PAID"))
				.andExpect(jsonPath("$.booking.bookingStatus").value("COMPLETED"));

		// Another job, finished but not paid: NexHome completes it, the payment stays pending
		long second = book("ONLINE", 14);
		String secondBody = "{\"bookingId\": %d}".formatted(second);
		perform(put("/api/bookings/status/update"), staff,
				"{\"bookingId\": %d, \"bookingStatus\": \"CONFIRMED\"}".formatted(second));
		perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d, \"direct\": true, \"outsideWeeklyHours\": true}"
						.formatted(second, partnerId))
				.andExpect(status().isCreated());
		startWithCode(partner, customer, second);
		perform(put("/api/bookings/complete"), staff, secondBody)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Only a job whose work is finished can be completed here."));
		perform(put("/api/bookings/completion/request"), partner, secondBody).andExpect(status().isOk());
		perform(put("/api/bookings/completion/respond"), customer,
				"{\"bookingId\": %d, \"completed\": true}".formatted(second))
				.andExpect(status().isOk());
		perform(put("/api/bookings/complete"), partner, secondBody).andExpect(status().isForbidden());
		perform(put("/api/bookings/complete"), staff, secondBody)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.booking.bookingStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.booking.paymentStatus").value("PENDING"))
				.andExpect(jsonPath("$.history[?(@.reason == 'Completed by NexHome; payment pending')]").exists());
	}

	/**
	 * An online job is finished but not paid: the partner checks the payment, then collects cash instead. The booking
	 * becomes a cash booking with its cash collected, the open order is cancelled, and the history shows both.
	 */
	@Test
	void thePartnerCollectsCashWhenTheOnlinePaymentDoesNotGoThrough() throws Exception {
		setUp();
		long bookingId = book("ONLINE", 10);
		long orderId = json(perform(post("/api/payments/orders/create"), customer,
				"{\"bookingId\": %d}".formatted(bookingId))).get("paymentOrderId").asLong();
		String body = "{\"bookingId\": %d}".formatted(bookingId);
		finish(bookingId);
		// Finished without the online payment: completed, the payment still pending (the customer pays afterwards)
		perform(get("/api/bookings/" + bookingId), customer, null)
				.andExpect(jsonPath("$.booking.bookingStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.booking.paymentStatus").value("PENDING"))
				.andExpect(jsonPath("$.booking.actualEndAt").isNotEmpty());

		perform(post("/api/payments/bookings/check"), partner, body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paymentMethod").value("ONLINE"))
				.andExpect(jsonPath("$.paymentStatus").value("PENDING"))
				.andExpect(jsonPath("$.onlinePaymentOpen").value(true))
				.andExpect(jsonPath("$.totalAmount").value(400));
		// Only the partner (or staff) records cash; the customer cannot
		perform(post("/api/payments/bookings/collect-cash"), customer, body).andExpect(status().isForbidden());

		perform(post("/api/payments/bookings/collect-cash"), partner,
				"{\"bookingId\": %d, \"notes\": \"UPI kept failing\"}".formatted(bookingId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paymentMethod").value("CASH"))
				.andExpect(jsonPath("$.paymentStatus").value("PAID"))
				.andExpect(jsonPath("$.onlinePaymentOpen").value(false));
		perform(get("/api/payments/bookings/" + bookingId), staff, null)
				.andExpect(jsonPath("$.cashCollection.status").value("COLLECTED"))
				.andExpect(jsonPath("$.cashCollection.amount").value(400))
				.andExpect(jsonPath("$.cashCollection.notes").value("UPI kept failing"))
				.andExpect(jsonPath("$.orders[0].paymentStatus").value("CANCELLED"))
				.andExpect(jsonPath("$.payout").doesNotExist());
		perform(get("/api/payments/orders/" + orderId), staff, null)
				.andExpect(jsonPath("$.order.paymentStatus").value("CANCELLED"));
		perform(get("/api/bookings/" + bookingId), customer, null)
				.andExpect(jsonPath("$.booking.paymentMethod").value("CASH"))
				.andExpect(jsonPath("$.booking.paymentStatus").value("PAID"))
				.andExpect(jsonPath("$.booking.bookingStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.history[?(@.reason == 'Payment mode changed from online to cash')]").exists())
				.andExpect(jsonPath("$.history[?(@.reason == 'Cash collected')]").exists());
		// Once is enough
		perform(post("/api/payments/bookings/collect-cash"), partner, body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"This booking is paid in cash already; mark its cash collected instead."));
	}

	@Test
	void aSignedWebhookPaysAndAnUnsignedOneIsRefused() throws Exception {
		setUp();
		long bookingId = book("ONLINE", 10);
		String providerOrderId = json(perform(post("/api/payments/orders/create"), customer,
				"{\"bookingId\": %d}".formatted(bookingId))).get("providerOrderId").asString();
		String eventId = "evt_" + UUID.randomUUID();
		String forged = """
				{"id": "%s", "type": "payment.captured", "data": {"orderId": "%s", "paymentId": "pay_%s"}}"""
				.formatted(eventId, providerOrderId, eventId);

		perform(post("/api/payments/webhooks/MANUAL").header("X-NexHome-Signature", "bad"), null, forged)
				.andExpect(status().isUnauthorized());
		// The forged delivery is kept but never acted on; a genuine one (its own id) is processed, once
		String genuine = forged.replace(eventId + "\"", eventId + "-2\"");
		perform(post("/api/payments/webhooks/MANUAL").header("X-NexHome-Signature", sign(genuine)), null, genuine)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.processingStatus").value("PROCESSED"));
		perform(post("/api/payments/webhooks/MANUAL").header("X-NexHome-Signature", sign(genuine)), null, genuine)
				.andExpect(jsonPath("$.processingStatus").value("PROCESSED"));
		perform(get("/api/bookings/" + bookingId), customer, null)
				.andExpect(jsonPath("$.booking.paymentStatus").value("PAID"));
		perform(post("/api/payments/webhooks/OTHER").header("X-NexHome-Signature", sign(genuine)), null, genuine)
				.andExpect(status().isNotFound());
	}

	@Test
	void cashIsCollectedByThePartner() throws Exception {
		setUp();
		long bookingId = book("CASH", 10);
		finish(bookingId);
		JsonNode cash = json(perform(get("/api/payments/bookings/" + bookingId), staff, null)
				.andExpect(jsonPath("$.cashCollection.status").value("DUE"))
				.andExpect(jsonPath("$.cashCollection.amount").value(400))
				.andExpect(jsonPath("$.payout").doesNotExist())).get("cashCollection");
		long cashId = cash.get("cashCollectionId").asLong();
		// The assigned partner finds the cash collection (the partner app), but none of the payments
		perform(get("/api/payments/bookings/" + bookingId), partner, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cashCollection.cashCollectionId").value(cashId))
				.andExpect(jsonPath("$.orders.length()").value(0))
				.andExpect(jsonPath("$.payout").doesNotExist());
		perform(put("/api/payments/cash-collections/update"), customer,
				"{\"cashCollectionId\": %d, \"status\": \"COLLECTED\"}".formatted(cashId))
				.andExpect(status().isForbidden());
		perform(put("/api/payments/cash-collections/update"), partner,
				"{\"cashCollectionId\": %d, \"status\": \"WAIVED\"}".formatted(cashId))
				.andExpect(status().isForbidden());
		perform(put("/api/payments/cash-collections/update"), partner,
				"{\"cashCollectionId\": %d, \"status\": \"COLLECTED\"}".formatted(cashId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COLLECTED"))
				.andExpect(jsonPath("$.confirmedAt").isNotEmpty());
		perform(put("/api/payments/cash-collections/acknowledge"), customer,
				"{\"cashCollectionId\": %d}".formatted(cashId))
				.andExpect(jsonPath("$.customerAcknowledgedAt").isNotEmpty());
		perform(get("/api/bookings/" + bookingId), customer, null)
				.andExpect(jsonPath("$.booking.paymentStatus").value("PAID"));
		// The partner's earnings: the cash less the service's 15% commission, today and overall
		for (String period : List.of("TODAY", "ALL")) {
			perform(get("/api/payouts/earnings/" + partnerId + "?period=" + period), partner, null)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.jobs").value(1))
					.andExpect(jsonPath("$.totalAmount").value(400))
					.andExpect(jsonPath("$.commission").value(60))
					.andExpect(jsonPath("$.earnings").value(340))
					.andExpect(jsonPath("$.receivedEarnings").value(340))
					.andExpect(jsonPath("$.pendingEarnings").value(0))
					.andExpect(jsonPath("$.pendingJobs").value(0))
					.andExpect(jsonPath("$.records.content[0].settlement").value("CASH"))
					.andExpect(jsonPath("$.records.content[0].settlementStatus").value("COLLECTED"));
		}
		perform(get("/api/payouts/earnings/" + partnerId + "?period=YEAR"), staff, null)
				.andExpect(status().isBadRequest());
		perform(get("/api/payouts/earnings/" + partnerId), customer, null).andExpect(status().isForbidden());
		Number count = (Number) entityManager.createNativeQuery(
				"select count(*) from nexhome_partner_payouts where booking_id = :id").setParameter("id", bookingId)
				.getSingleResult();
		assertThat(count.intValue()).isZero();
	}

	/**
	 * What NexHome owes a partner (V62): an online job and a cash job (the partner hands the cash over), each 400 less
	 * the 15% commission, so 680; staff record payments made outside the app, which bring the balance down.
	 */
	@Test
	void nexHomeOwesEveryJobLessCommissionAndRecordedPayoutsComeOff() throws Exception {
		setUp();
		long online = book("ONLINE", 10);
		long orderId = json(perform(post("/api/payments/orders/create"), customer,
				"{\"bookingId\": %d}".formatted(online))).get("paymentOrderId").asLong();
		perform(post("/api/payments/orders/capture"), staff, "{\"paymentOrderId\": %d}".formatted(orderId))
				.andExpect(status().isOk());
		finish(online);
		long cash = book("CASH", 10);
		finish(cash);
		long cashId = json(perform(get("/api/payments/bookings/" + cash), partner, null)).get("cashCollection")
				.get("cashCollectionId").asLong();
		perform(put("/api/payments/cash-collections/update"), partner,
				"{\"cashCollectionId\": %d, \"status\": \"COLLECTED\"}".formatted(cashId))
				.andExpect(status().isOk());

		perform(get("/api/payouts/wallet/" + partnerId), customer, null).andExpect(status().isForbidden());
		perform(get("/api/payouts/wallet/" + partnerId), partner, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cashJobs").value(1))
				.andExpect(jsonPath("$.cashAmount").value(400))
				.andExpect(jsonPath("$.cashCommission").value(60))
				.andExpect(jsonPath("$.onlineJobs").value(1))
				.andExpect(jsonPath("$.onlineAmount").value(400))
				.andExpect(jsonPath("$.onlineCommission").value(60))
				.andExpect(jsonPath("$.totalAmount").value(800))
				.andExpect(jsonPath("$.totalCommission").value(120))
				.andExpect(jsonPath("$.totalEarned").value(680))
				.andExpect(jsonPath("$.paidTotal").value(0))
				.andExpect(jsonPath("$.balance").value(680))
				.andExpect(jsonPath("$.lastPayout").doesNotExist())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].effect").value(340));
		String partnerPublicId = json(perform(get("/api/partners/profiles/" + partnerId), staff, null)).get("publicId")
				.asString();
		perform(get("/api/payouts/dues?search=" + partnerPublicId), staff, null)
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].jobs").value(2))
				.andExpect(jsonPath("$.content[0].balance").value(680));

		// Recording a payment: checked, then it comes off what is owed
		String body = """
				{"partnerProfileId": %d, "amount": %s, "paymentMode": "%s", "transactionId": %s, "paidOn": "%s"}""";
		String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
		perform(post("/api/payouts/manual/create"), partner, body.formatted(partnerId, "100", "PHONEPE", "\"T1\"", today))
				.andExpect(status().isForbidden());
		perform(post("/api/payouts/manual/create"), staff, body.formatted(partnerId, "100", "PHONEPE", "null", today))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Transaction ID is required unless the partner was paid in cash."));
		perform(post("/api/payouts/manual/create"), staff, body.formatted(partnerId, "700", "PHONEPE", "\"T1\"", today))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The amount is more than NexHome owes the partner (680.00)."));
		perform(post("/api/payouts/manual/create"), staff,
				body.formatted(partnerId, "100", "PHONEPE", "\"T1\"", java.time.LocalDate.parse(today).plusDays(2)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Payment date cannot be in the future."));
		perform(post("/api/payouts/manual/create"), staff, body.formatted(partnerId, "100", "UPI_APP", "\"T1\"", today))
				.andExpect(status().isBadRequest());
		long payoutId = json(perform(post("/api/payouts/manual/create"), staff,
				body.formatted(partnerId, "500", "GOOGLE_PAY", "\"4321UTR\"", today))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.publicId").value(org.hamcrest.Matchers.startsWith("NHPPAY")))
				.andExpect(jsonPath("$.amount").value(500))
				.andExpect(jsonPath("$.paymentMode").value("GOOGLE_PAY"))
				.andExpect(jsonPath("$.transactionId").value("4321UTR"))
				.andExpect(jsonPath("$.paidOn").value(today))
				.andExpect(jsonPath("$.payoutStatus").value("RECORDED"))).get("payoutId").asLong();
		perform(post("/api/payouts/manual/create"), staff, body.formatted(partnerId, "30", "CASH", "null", today))
				.andExpect(status().isCreated());

		perform(get("/api/payouts/wallet/" + partnerId), partner, null)
				.andExpect(jsonPath("$.paidTotal").value(530))
				.andExpect(jsonPath("$.payoutCount").value(2))
				.andExpect(jsonPath("$.balance").value(150))
				.andExpect(jsonPath("$.lastPayout.amount").isNumber());
		perform(get("/api/payouts/manual?partnerProfileId=" + partnerId), partner, null)
				.andExpect(jsonPath("$.totalElements").value(2));
		perform(get("/api/payouts/manual/" + payoutId), partner, null).andExpect(status().isOk());
		perform(get("/api/payouts/manual/" + payoutId), customer, null).andExpect(status().isForbidden());
		perform(get("/api/payouts/manual"), partner, null).andExpect(status().isForbidden());
		perform(get("/api/payouts/manual/stats"), staff, null)
				.andExpect(jsonPath("$.paidTotal").isNumber())
				.andExpect(jsonPath("$.totalDue").isNumber());

		// A payout recorded by mistake is cancelled: it no longer counts
		perform(put("/api/payouts/manual/cancel"), staff, "{\"payoutId\": %d, \"reason\": \"Recorded twice\"}"
				.formatted(payoutId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payoutStatus").value("CANCELLED"))
				.andExpect(jsonPath("$.cancellationReason").value("Recorded twice"));
		perform(put("/api/payouts/manual/cancel"), staff, "{\"payoutId\": %d, \"reason\": \"Again\"}"
				.formatted(payoutId))
				.andExpect(status().isBadRequest());
		perform(get("/api/payouts/wallet/" + partnerId), staff, null)
				.andExpect(jsonPath("$.paidTotal").value(30))
				.andExpect(jsonPath("$.balance").value(650));

		// Deductions (V63): NexHome keeps part of what it owes, with a reason; with a payout or on their own
		String withDeduction = """
				{"partnerProfileId": %d, "amount": %s, "paymentMode": "PHONEPE", "transactionId": "T9", "paidOn": "%s",
				 "deductionAmount": %s, "deductionReason": "%s", "deductionNotes": %s}""";
		perform(post("/api/payouts/manual/create"), staff,
				withDeduction.formatted(partnerId, "600", today, "100", "PENALTY", "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"The payment and the deduction together are more than NexHome owes the partner (650.00)."));
		perform(post("/api/payouts/manual/create"), staff,
				withDeduction.formatted(partnerId, "300", today, "50", "OTHER", "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Add a note saying what the deduction is for."));
		long withId = json(perform(post("/api/payouts/manual/create"), staff,
				withDeduction.formatted(partnerId, "300", today, "50", "PENALTY", "\"Late for the booking\""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.deduction.publicId").value(org.hamcrest.Matchers.startsWith("NHPDED")))
				.andExpect(jsonPath("$.deduction.amount").value(50))
				.andExpect(jsonPath("$.deduction.deductionReason").value("PENALTY"))
				.andExpect(jsonPath("$.deduction.payoutPublicId").isNotEmpty())).get("deduction").get("deductionId")
				.asLong();
		String alone = """
				{"partnerProfileId": %d, "amount": %s, "deductionReason": "DAMAGE", "notes": "Broken tap", "deductedOn": "%s"}""";
		perform(post("/api/payouts/deductions/create"), staff, alone.formatted(partnerId, "400", today))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The deduction is more than NexHome owes the partner (300.00)."));
		perform(post("/api/payouts/deductions/create"), partner, alone.formatted(partnerId, "100", today))
				.andExpect(status().isForbidden());
		perform(post("/api/payouts/deductions/create"), staff, alone.formatted(partnerId, "100", today))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.payoutId").doesNotExist())
				.andExpect(jsonPath("$.status").value("RECORDED"));
		perform(get("/api/payouts/wallet/" + partnerId), partner, null)
				.andExpect(jsonPath("$.paidTotal").value(330))
				.andExpect(jsonPath("$.deductedTotal").value(150))
				.andExpect(jsonPath("$.deductionCount").value(2))
				.andExpect(jsonPath("$.balance").value(200));
		perform(get("/api/payouts/deductions?partnerProfileId=" + partnerId), partner, null)
				.andExpect(jsonPath("$.totalElements").value(2));
		perform(get("/api/payouts/deductions"), partner, null).andExpect(status().isForbidden());
		perform(get("/api/payouts/dues?search=" + partnerPublicId), staff, null)
				.andExpect(jsonPath("$.content[0].deductedTotal").value(150))
				.andExpect(jsonPath("$.content[0].balance").value(200));
		// A deduction recorded by mistake is cancelled: it no longer counts
		perform(put("/api/payouts/deductions/cancel"), staff,
				"{\"deductionId\": %d, \"reason\": \"Not late after all\"}".formatted(withId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		perform(get("/api/payouts/wallet/" + partnerId), staff, null)
				.andExpect(jsonPath("$.deductedTotal").value(100))
				.andExpect(jsonPath("$.balance").value(250));

		// An approved partner's bank accounts are locked for the partner (staff still add them)
		perform(post("/api/partners/bank-accounts/add"), partner, """
				{"partnerProfileId": %d, "accountHolderName": "Kiran Rao", "accountNumber": "123456789012",
				 "ifscCode": "SBIN0001234", "bankName": "SBI", "branchName": "Madhapur"}""".formatted(partnerId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"Your bank account details are locked after approval. Contact NexHome support to change them."));
		long accountId = json(perform(post("/api/partners/bank-accounts/add"), staff, """
				{"partnerProfileId": %d, "accountHolderName": "Kiran Rao", "accountNumber": "123456784321",
				 "ifscCode": "SBIN0001234", "bankName": "SBI", "branchName": "Madhapur"}""".formatted(partnerId))
				.andExpect(status().isCreated())).get("partnerBankAccountId").asLong();
		// Once the profile was sent for review the number and IFSC are fixed, for staff too
		perform(put("/api/partners/bank-accounts/update"), staff,
				"{\"partnerBankAccountId\": %d, \"accountNumber\": \"999999999\"}".formatted(accountId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The account number and IFSC cannot be changed after the profile "
						+ "is sent for review; add a new bank account instead."));
	}

	/** An approved partner may still change their working hours and areas, but nothing else of the profile. */
	@Test
	void anApprovedPartnerChangesOnlyWorkingHoursAndAreas() throws Exception {
		setUp();
		perform(put("/api/partners/profiles/update"), partner,
				"{\"partnerProfileId\": %d, \"maxTravelKm\": 25, \"serviceAreas\": [\"Madhapur\", \"Kondapur\"]}"
						.formatted(partnerId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.maxTravelKm").value(25))
				.andExpect(jsonPath("$.serviceAreas.length()").value(2))
				.andExpect(jsonPath("$.onboardingStatus").value("APPROVED"));
		perform(put("/api/partners/profiles/update"), partner,
				"{\"partnerProfileId\": %d, \"firstName\": \"Ravi\", \"maxTravelKm\": 20}".formatted(partnerId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The profile is APPROVED and cannot be changed now. Contact support to update it."));
	}

	/** A partner who went offline is online again once they start the work. */
	@Test
	void startingWorkPutsThePartnerOnline() throws Exception {
		setUp();
		long bookingId = book("CASH", 10);
		perform(put("/api/bookings/status/update"), staff,
				"{\"bookingId\": %d, \"bookingStatus\": \"CONFIRMED\"}".formatted(bookingId));
		perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d, \"direct\": true}".formatted(bookingId, partnerId))
				.andExpect(status().isCreated());
		perform(put("/api/partners/profiles/availability/update"), partner,
				"{\"partnerProfileId\": %d, \"availabilityStatus\": \"OFFLINE\"}".formatted(partnerId))
				.andExpect(status().isOk());
		perform(get("/api/partners/profiles/me"), partner, null)
				.andExpect(jsonPath("$.availabilityStatus").value("OFFLINE"));
		startWithCode(partner, customer, bookingId);
		perform(get("/api/partners/profiles/me"), partner, null)
				.andExpect(jsonPath("$.availabilityStatus").value("ONLINE"));
	}

	/** A category, a FIXED service at 400 (15% platform commission), a customer with an address and a partner. */
	private void setUp() throws Exception {
		staff = accessToken(newUser("SUPER_USER").getId());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		categoryId = json(perform(post("/api/catalog/categories/create"), staff,
				"{\"name\": \"Payments %s\"}".formatted(suffix))
				.andExpect(status().isCreated())).get("serviceCategoryId").asLong();
		serviceId = json(perform(post("/api/catalog/services/create"), staff, """
				{"categoryId": %d, "name": "AC Service %s", "serviceTarget": "APPLIANCE", "pricingType": "FIXED",
				 "basePrice": 400, "minimumCharge": 0, "estimatedDurationMinutes": 60,
				 "platformCommissionType": "PERCENTAGE", "platformCommissionValue": 15, "isActive": true}"""
				.formatted(categoryId, suffix))
				.andExpect(status().isCreated())).get("serviceId").asLong();
		NexHomeUser customerUser = newUser("CUSTOMER");
		customer = accessToken(customerUser.getId());
		customerId = json(perform(post("/api/profiles/customers/create"), customer, """
				{"userId": %d, "firstName": "Anitha", "lastName": "Rao"}""".formatted(customerUser.getId()))
				.andExpect(status().isCreated())).get("customerProfileId").asLong();
		addressId = json(perform(post("/api/addresses/create"), customer, """
				{"userId": %d, "contactName": "Anitha", "mobileNumber": "9876543210", "addressType": "HOME",
				 "label": "Home", "addressLine1": "Plot 12", "city": "Hyderabad", "state": "Telangana",
				 "postalCode": "500081"}""".formatted(customerUser.getId()))
				.andExpect(status().isCreated())).get("addressId").asLong();
		NexHomeUser partnerUser = newUser("PARTNER");
		partner = accessToken(partnerUser.getId());
		partnerId = approvedPartner(staff, partnerUser, categoryId, serviceId);
		monday = LocalDate.now(IST).plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	/** A staff booking at the hour on the coming Monday. */
	private long book(String paymentMethod, int hour) throws Exception {
		return json(perform(post("/api/bookings/create"), staff, """
				{"customerProfileId": %d, "serviceId": %d, "addressId": %d, "bookingType": "SCHEDULED",
				 "scheduledStartAt": "%s", "paymentMethod": "%s"}""".formatted(customerId, serviceId, addressId,
				monday.atTime(hour, 0).atZone(IST).toInstant(), paymentMethod))
				.andExpect(status().isCreated())).get("bookingId").asLong();
	}

	/** Assigned and done: confirmed if need be, assigned at once, the partner starts and stops the timer. */
	private void finish(long bookingId) throws Exception {
		perform(put("/api/bookings/status/update"), staff,
				"{\"bookingId\": %d, \"bookingStatus\": \"CONFIRMED\"}".formatted(bookingId));
		perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d, \"direct\": true}".formatted(bookingId, partnerId))
				.andExpect(status().isCreated());
		startWithCode(partner, customer, bookingId);
		perform(post("/api/bookings/timer"), partner,
				"{\"bookingId\": %d, \"eventType\": \"STOP\"}".formatted(bookingId))
				.andExpect(status().isCreated());
	}

	private static String sign(String body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
	}

	/** An approved partner who offers the service and works Monday mornings and evenings. */
	private long approvedPartner(String staff, NexHomeUser partnerUser, long categoryId, long serviceId)
			throws Exception {
		long partnerId = json(perform(post("/api/partners/profiles/create"), staff, """
				{"userId": %d, "firstName": "Kiran", "lastName": "Rao"}""".formatted(partnerUser.getId()))
				.andExpect(status().isCreated())).get("partnerProfileId").asLong();
		perform(put("/api/partners/profiles/update"), staff, """
				{"partnerProfileId": %d, "mainServiceCategoryId": %d, "isAdult": true, "preferredContactLanguage": "te",
				 "aadhaarNumber": "234567890123", "addressLine1": "H.No 1-2-3", "locality": "Madhapur",
				 "city": "Hyderabad", "state": "Telangana", "postalCode": "500081", "skillCategory": "SKILLED",
				 "yearsExperience": 6, "preferredArrangement": "FLEXIBLE_JOBS", "paymentBasis": "PER_JOB",
				 "expectedEarningsInr": 800, "maxTravelKm": 10, "serviceAreas": ["Madhapur"],
				 "weeklyAvailability": {"MONDAY": ["MORNING", "EVENING"]}, "weekendAvailability": "NEITHER",
				 "emergencyContactName": "Lakshmi", "emergencyContactRelationship": "SPOUSE",
				 "emergencyContactNumber": "9000000001"}""".formatted(partnerId, categoryId))
				.andExpect(status().isOk());
		perform(post("/api/partners/services/add"), staff,
				"{\"partnerProfileId\": %d, \"serviceId\": %d}".formatted(partnerId, serviceId))
				.andExpect(status().isCreated());
		perform(put("/api/partners/profiles/submit"), staff, "{\"partnerProfileId\": %d}".formatted(partnerId))
				.andExpect(status().isOk());
		entityManager.createNativeQuery("update nexhome_partner_profiles set onboarding_status = 'APPROVED', "
				+ "approval_status = 'VERIFIED', approved_by = 1, approved_at = now() where id = :id")
				.setParameter("id", partnerId).executeUpdate();
		entityManager.clear();
		return partnerId;
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String accessToken, String body)
			throws Exception {
		if (accessToken != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private JsonNode json(ResultActions result) throws Exception {
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	/** An ACTIVE account with one role, created inside the test's transaction. */
	private NexHomeUser newUser(String roleCode) {
		NexHomeUser user = userRepository.save(userMapper.toUserEntity("+91", randomMobile(), null, null, 1L));
		userRoleRepository.saveAndFlush(userMapper.toUserRoleEntity(user,
				roleRepository.findByCode(roleCode).orElseThrow(), 1L));
		return user;
	}

	/** A login session and its access token, as the auth service would issue them. */
	private String accessToken(Long userId) {
		Instant now = Instant.now();
		UUID sessionId = UUID.randomUUID();
		refreshTokenRepository.saveAndFlush(refreshTokenMapper.toRefreshTokenEntity(
				userRepository.findById(userId).orElseThrow(), sessionId, UUID.randomUUID().toString().replace("-", "")
						+ UUID.randomUUID().toString().replace("-", ""),
				now.plusSeconds(3600), "JUnit"));
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(securityProperties.jwtIssuer())
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", sessionId.toString())
				.claim("roles", List.of())
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	private static String randomMobile() {
		return "8" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
	}

	/**
	 * The partner accepts the booking (if not yet), the customer reads its start code (V59), and the partner starts the
	 * work with it.
	 */
	private void startWithCode(String partnerToken, String customerToken, long bookingId) throws Exception {
		String status = json(perform(get("/api/bookings/" + bookingId), customerToken, null)).get("booking")
				.get("bookingStatus").asText();
		if ("ASSIGNED".equals(status)) {
			perform(put("/api/bookings/status/update"), partnerToken,
					"{\"bookingId\": %d, \"bookingStatus\": \"PARTNER_EN_ROUTE\"}".formatted(bookingId))
					.andExpect(status().isOk());
		}
		String code = json(perform(get("/api/bookings/" + bookingId), customerToken, null)).get("booking")
				.get("startOtp").asText();
		perform(post("/api/bookings/timer"), partnerToken,
				"{\"bookingId\": %d, \"eventType\": \"START\", \"otp\": \"%s\"}".formatted(bookingId, code))
				.andExpect(status().isCreated());
	}
}
