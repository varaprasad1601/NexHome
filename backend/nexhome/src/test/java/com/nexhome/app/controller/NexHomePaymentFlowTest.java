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
		// Online bookings carry the 2% transaction fee (V35): 400 + 8
		perform(get("/api/bookings/" + bookingId), customer, null)
				.andExpect(jsonPath("$.booking.transactionFeeAmount").value(8))
				.andExpect(jsonPath("$.booking.totalAmount").value(408));
		// The customer starts paying; asking again reuses the open order
		JsonNode order = json(perform(post("/api/payments/orders/create"), customer,
				"{\"bookingId\": %d}".formatted(bookingId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.amount").value(408))
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
				.andExpect(jsonPath("$.history[1].reason").value("Payment received"));

		// Done: the partner is owed 400 (the fee is the platform's) less the service's 15% platform commission
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

		// A bonus, then paying needs a verified primary bank account
		perform(put("/api/payouts/adjust"), staff, "{\"payoutId\": %d, \"adjustmentsAmount\": 25}".formatted(payoutId))
				.andExpect(jsonPath("$.netAmount").value(365));
		perform(put("/api/payouts/adjust"), staff, "{\"payoutId\": %d, \"adjustmentsAmount\": -400}".formatted(payoutId))
				.andExpect(status().isBadRequest());
		perform(put("/api/payouts/pay"), staff, "{\"payoutId\": %d}".formatted(payoutId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The partner needs a verified primary bank account before being paid."));
		long accountId = json(perform(post("/api/partners/bank-accounts/add"), staff, """
				{"partnerProfileId": %d, "accountHolderName": "Kiran Rao", "accountNumber": "123456784321",
				 "ifscCode": "SBIN0001234", "bankName": "SBI", "branchName": "Madhapur"}""".formatted(partnerId))
				.andExpect(status().isCreated())).get("partnerBankAccountId").asLong();
		perform(put("/api/partners/bank-accounts/verify"), staff,
				"{\"partnerBankAccountId\": %d, \"verificationStatus\": \"VERIFIED\"}".formatted(accountId))
				.andExpect(status().isOk());
		perform(put("/api/payouts/pay"), staff, "{\"payoutId\": %d}".formatted(payoutId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payoutStatus").value("PAID"))
				.andExpect(jsonPath("$.paidAt").isNotEmpty())
				.andExpect(jsonPath("$.providerTransferId").isNotEmpty());
		perform(get("/api/payouts/me"), partner, null)
				.andExpect(jsonPath("$.content[0].payoutId").value(payoutId));
		// Once the partner is paid the booking is not refunded here
		perform(post("/api/payments/refunds/create"), staff,
				"{\"paymentId\": %d, \"amount\": 50, \"reason\": \"Late\"}".formatted(paymentId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"The partner's payout for this booking is already PAID; the booking cannot be refunded here."));
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
						"Refunds cannot exceed the captured amount less the transaction fee; 300.00 can still be refunded."));
		perform(get("/api/bookings/" + bookingId), customer, null)
				.andExpect(jsonPath("$.booking.paymentStatus").value("PARTIALLY_REFUNDED"));
		// Cancelling refunds the rest, but not the transaction fee
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
		for (String event : List.of("START", "STOP")) {
			perform(post("/api/bookings/timer"), partner,
					"{\"bookingId\": %d, \"eventType\": \"%s\"}".formatted(bookingId, event))
					.andExpect(status().isCreated());
		}
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
}
