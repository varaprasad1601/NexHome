package com.nexhome.app.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import com.nexhome.app.service.NexHomeBookingService;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The staff notifications (dev DB, rolled back): a partner signing up, resubmitting and adding a document under
 * review; a customer cancelling; an offer expiring and a booking starting soon without a partner (HIGH, once);
 * disputed cash (HIGH); urgent, raised and reopened tickets. One super user acts and another (the watcher) receives:
 * nobody is notified of their own actions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class NexHomeStaffNotificationFlowTest {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

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
	private NexHomeBookingService bookingService;
	@Autowired
	private EntityManager entityManager;

	private String staff;
	private String watcher;
	private long categoryId;
	private long serviceId;

	@Test
	void generalSettingsDecideStaffAlertsAndAreChecked() throws Exception {
		setUp();
		perform(get("/api/settings"), staff, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.settingKey == 'MAX_CART_ITEMS')].min").value(1.0))
				.andExpect(jsonPath("$[?(@.settingKey == 'ALERT_PARTNER_SIGNUP')].options[0]").value("OFF"));
		perform(get("/api/settings/public"), null, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.WHATSAPP_NUMBER").isNotEmpty())
				.andExpect(jsonPath("$.MAX_CART_ITEMS").doesNotExist());
		perform(get("/api/settings"), accessToken(newUser("CUSTOMER").getId()), null)
				.andExpect(status().isForbidden());
		// Only super users change them (V51): an admin reads but cannot save
		String admin = accessToken(newUser("ADMIN").getId());
		perform(get("/api/settings"), admin, null).andExpect(status().isOk());
		perform(put("/api/settings/update"), admin,
				"{\"settings\": [{\"settingKey\": \"MAX_CART_ITEMS\", \"value\": \"10\"}]}")
				.andExpect(status().isForbidden());

		// HIGH: the partner managers hear of a sign-up as High
		perform(put("/api/settings/update"), staff,
				"{\"settings\": [{\"settingKey\": \"ALERT_PARTNER_SIGNUP\", \"value\": \"high\"}]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.settingKey == 'ALERT_PARTNER_SIGNUP')].value").value("HIGH"));
		perform(post("/api/auth/register"), null,
				"{\"countryCode\": \"+91\", \"mobileNumber\": \"%s\", \"roleCode\": \"PARTNER\"}".formatted(randomMobile()))
				.andExpect(status().isCreated());
		latest("ACCOUNT").andExpect(jsonPath("$.content[0].priority").value("HIGH"));
		long signUps = json(latest("ACCOUNT")).get("totalElements").asLong();

		// OFF: nobody hears of it
		perform(put("/api/settings/update"), staff,
				"{\"settings\": [{\"settingKey\": \"ALERT_PARTNER_SIGNUP\", \"value\": \"OFF\"}]}")
				.andExpect(status().isOk());
		perform(post("/api/auth/register"), null,
				"{\"countryCode\": \"+91\", \"mobileNumber\": \"%s\", \"roleCode\": \"PARTNER\"}".formatted(randomMobile()))
				.andExpect(status().isCreated());
		latest("ACCOUNT").andExpect(jsonPath("$.totalElements").value(signUps));

		// Values are checked, all or none
		perform(put("/api/settings/update"), staff, """
				{"settings": [{"settingKey": "OFFER_EXPIRY_MINUTES", "value": "45"},
				              {"settingKey": "MAX_CART_ITEMS", "value": "0"}]}""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Cart Limit (services) must be between 1 and 50."));
		perform(get("/api/settings"), staff, null)
				.andExpect(jsonPath("$[?(@.settingKey == 'OFFER_EXPIRY_MINUTES')].value").value("30"));
		perform(put("/api/settings/update"), staff,
				"{\"settings\": [{\"settingKey\": \"NO_SUCH_SETTING\", \"value\": \"1\"}]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Unknown setting NO_SUCH_SETTING."));
		perform(put("/api/settings/update"), staff,
				"{\"settings\": [{\"settingKey\": \"WHATSAPP_NUMBER\", \"value\": \"+91 92571\"}]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("WhatsApp Number must be 10 to 15 digits with the country code."));
	}

	@Test
	void partnersSigningUpResubmittingAndAddingDocumentsTellStaff() throws Exception {
		setUp();
		// A partner signs up: the partner managers hear of it
		String mobile = randomMobile();
		perform(post("/api/auth/register"), null,
				"{\"countryCode\": \"+91\", \"mobileNumber\": \"%s\", \"roleCode\": \"PARTNER\"}".formatted(mobile))
				.andExpect(status().isCreated());
		latest("ACCOUNT")
				.andExpect(jsonPath("$.content[0].title").value("New partner signed up"))
				.andExpect(jsonPath("$.content[0].message").value(Matchers.containsString(mobile)))
				.andExpect(jsonPath("$.content[0].referenceType").value("PARTNER_PROFILE"))
				.andExpect(jsonPath("$.content[0].priority").value("NORMAL"));

		// Changes requested, then submitted again: "resubmitted"
		long partnerId = submittedPartner(newUser("PARTNER"));
		perform(put("/api/partners/profiles/review"), staff, """
				{"partnerProfileId": %d, "onboardingStatus": "CHANGES_REQUESTED", "rejectionReason": "Photo unclear"}"""
				.formatted(partnerId))
				.andExpect(status().isOk());
		perform(put("/api/partners/profiles/submit"), staff, "{\"partnerProfileId\": %d}".formatted(partnerId))
				.andExpect(status().isOk());
		latest("ONBOARDING")
				.andExpect(jsonPath("$.content[0].title").value(Matchers.endsWith("resubmitted for review")))
				.andExpect(jsonPath("$.content[0].referenceId").value(partnerId));

		// Under review, a new document needs verifying
		perform(post("/api/partners/documents/add"), staff, """
				{"partnerProfileId": %d, "documentType": "GOVERNMENT_ID", "idType": "PAN", "documentNumber": "ABCDE1234F",
				 "fileId": %d}"""
				.formatted(partnerId, documentFile(staff)))
				.andExpect(status().isCreated());
		latest("ONBOARDING")
				.andExpect(jsonPath("$.content[0].title").value(Matchers.startsWith("To verify: partner")))
				.andExpect(jsonPath("$.content[0].message").value(Matchers.endsWith("added a document; verify it.")));
	}

	@Test
	void bookingsCancelledExpiredOrUnassignedAndDisputedCashTellStaff() throws Exception {
		setUp();
		NexHomeUser customerUser = newUser("CUSTOMER");
		String customer = accessToken(customerUser.getId());
		long customerId = json(perform(post("/api/profiles/customers/create"), customer, """
				{"userId": %d, "firstName": "Ravi", "lastName": "Teja"}""".formatted(customerUser.getId()))
				.andExpect(status().isCreated())).get("customerProfileId").asLong();
		long addressId = json(perform(post("/api/addresses/create"), customer, """
				{"userId": %d, "contactName": "Ravi", "mobileNumber": "9876543210", "addressType": "HOME",
				 "label": "Home", "addressLine1": "Plot 3", "city": "Hyderabad", "state": "Telangana",
				 "postalCode": "500081"}""".formatted(customerUser.getId()))
				.andExpect(status().isCreated())).get("addressId").asLong();
		NexHomeUser partnerUser = newUser("PARTNER");
		String partner = accessToken(partnerUser.getId());
		long partnerId = approvedPartner(partnerUser);
		LocalDate monday = LocalDate.now(IST).plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

		// The customer cancels
		long cancelledId = confirmedBooking(customerId, addressId, monday.atTime(10, 0).atZone(IST).toInstant());
		perform(put("/api/bookings/cancel"), customer,
				"{\"bookingId\": %d, \"reason\": \"Not at home\"}".formatted(cancelledId))
				.andExpect(status().isOk());
		latest("BOOKING")
				.andExpect(jsonPath("$.content[0].title").value(Matchers.endsWith("cancelled by the customer")))
				.andExpect(jsonPath("$.content[0].referenceId").value(cancelledId));

		// An offer nobody answers: HIGH
		long offeredId = confirmedBooking(customerId, addressId, monday.atTime(10, 0).atZone(IST).toInstant());
		long assignmentId = json(perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d}".formatted(offeredId, partnerId))
				.andExpect(status().isCreated())).get("assignmentId").asLong();
		entityManager.createNativeQuery("update nexhome_booking_assignments set expires_at = now() - interval '1 minute' "
				+ "where id = :id").setParameter("id", assignmentId).executeUpdate();
		entityManager.clear();
		bookingService.expireOffers();
		perform(get("/api/notifications/me?size=50&notificationType=BOOKING"), watcher, null)
				.andExpect(jsonPath("$.content[?(@.title == 'Offer expired: %s')].priority".formatted(publicId(offeredId)))
						.value(Matchers.hasItem("HIGH")));

		// Starting within 2 hours without a partner: HIGH, and only once
		long soonId = confirmedBooking(customerId, addressId, Instant.now().plus(70, ChronoUnit.MINUTES));
		bookingService.alertUnassignedBookings();
		bookingService.alertUnassignedBookings();
		String soonTitle = "No partner yet: " + publicId(soonId);
		perform(get("/api/notifications/me?size=50&notificationType=BOOKING"), watcher, null)
				.andExpect(jsonPath("$.content[?(@.title == '%s')]".formatted(soonTitle)).value(Matchers.hasSize(1)))
				.andExpect(jsonPath("$.content[?(@.title == '%s')].priority".formatted(soonTitle))
						.value(Matchers.hasItem("HIGH")));

		// Disputed cash: HIGH
		long cashBooking = confirmedBooking(customerId, addressId, monday.atTime(10, 0).atZone(IST).toInstant());
		perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d, \"direct\": true}".formatted(cashBooking, partnerId))
				.andExpect(status().isCreated());
		for (String event : List.of("START", "STOP")) {
			perform(post("/api/bookings/timer"), partner,
					"{\"bookingId\": %d, \"eventType\": \"%s\"}".formatted(cashBooking, event))
					.andExpect(status().isCreated());
		}
		long cashId = json(perform(get("/api/payments/bookings/" + cashBooking), staff, null))
				.get("cashCollection").get("cashCollectionId").asLong();
		perform(put("/api/payments/cash-collections/update"), staff,
				"{\"cashCollectionId\": %d, \"status\": \"DISPUTED\", \"notes\": \"Customer says paid by UPI\"}"
						.formatted(cashId))
				.andExpect(status().isOk());
		latest("PAYMENT")
				.andExpect(jsonPath("$.content[0].title").value("Cash disputed: " + publicId(cashBooking)))
				.andExpect(jsonPath("$.content[0].priority").value("HIGH"));
	}

	@Test
	void urgentRaisedAndReopenedTicketsTellStaff() throws Exception {
		setUp();
		NexHomeUser customerUser = newUser("CUSTOMER");
		String customer = accessToken(customerUser.getId());
		// Opened as urgent: HIGH
		long urgentId = json(perform(post("/api/support/tickets/create"), customer, """
				{"category": "PAYMENT", "subject": "Charged twice", "description": "Two debits", "priority": "URGENT"}""")
				.andExpect(status().isCreated())).get("supportTicketId").asLong();
		latest("SUPPORT")
				.andExpect(jsonPath("$.content[0].referenceId").value(urgentId))
				.andExpect(jsonPath("$.content[0].priority").value("HIGH"));

		// Opened normally (NORMAL), then raised to High (HIGH)
		long ticketId = json(perform(post("/api/support/tickets/create"), customer, """
				{"category": "ACCOUNT", "subject": "Cannot change my email", "description": "It fails"}""")
				.andExpect(status().isCreated())).get("supportTicketId").asLong();
		latest("SUPPORT").andExpect(jsonPath("$.content[0].priority").value("NORMAL"));
		perform(put("/api/support/tickets/update"), staff,
				"{\"supportTicketId\": %d, \"priority\": \"HIGH\"}".formatted(ticketId))
				.andExpect(status().isOk());
		latest("SUPPORT")
				.andExpect(jsonPath("$.content[0].title").value(Matchers.endsWith("raised to High")))
				.andExpect(jsonPath("$.content[0].priority").value("HIGH"));

		// Resolved, then the customer writes again: "reopened"
		perform(put("/api/support/tickets/update"), staff,
				"{\"supportTicketId\": %d, \"status\": \"RESOLVED\"}".formatted(ticketId))
				.andExpect(status().isOk());
		perform(post("/api/support/tickets/messages/add"), customer,
				"{\"supportTicketId\": %d, \"message\": \"Still failing\"}".formatted(ticketId))
				.andExpect(status().isCreated());
		latest("SUPPORT")
				.andExpect(jsonPath("$.content[0].title").value(Matchers.endsWith("reopened by the requester")));
	}

	/** The watcher's newest notification of a type. */
	private ResultActions latest(String type) throws Exception {
		return perform(get("/api/notifications/me?size=1&notificationType=" + type), watcher, null)
				.andExpect(status().isOk());
	}

	/** Two super users (one acts, the other receives), a category and a FIXED service at 300 (10% commission). */
	private void setUp() throws Exception {
		staff = accessToken(newUser("SUPER_USER").getId());
		watcher = accessToken(newUser("SUPER_USER").getId());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		categoryId = json(perform(post("/api/catalog/categories/create"), staff,
				"{\"name\": \"Alerts %s\"}".formatted(suffix))
				.andExpect(status().isCreated())).get("serviceCategoryId").asLong();
		serviceId = json(perform(post("/api/catalog/services/create"), staff, """
				{"categoryId": %d, "name": "Fan Repair %s", "serviceTarget": "APPLIANCE", "pricingType": "FIXED",
				 "basePrice": 300, "minimumCharge": 0, "estimatedDurationMinutes": 60,
				 "platformCommissionType": "PERCENTAGE", "platformCommissionValue": 10, "isActive": true}"""
				.formatted(categoryId, suffix))
				.andExpect(status().isCreated())).get("serviceId").asLong();
	}

	/** A staff CASH booking for the customer at the time, confirmed. */
	private long confirmedBooking(long customerId, long addressId, Instant startsAt) throws Exception {
		long bookingId = json(perform(post("/api/bookings/create"), staff, """
				{"customerProfileId": %d, "serviceId": %d, "addressId": %d, "bookingType": "SCHEDULED",
				 "scheduledStartAt": "%s", "paymentMethod": "CASH"}""".formatted(customerId, serviceId, addressId,
				startsAt))
				.andExpect(status().isCreated())).get("bookingId").asLong();
		perform(put("/api/bookings/status/update"), staff,
				"{\"bookingId\": %d, \"bookingStatus\": \"CONFIRMED\"}".formatted(bookingId))
				.andExpect(status().isOk());
		return bookingId;
	}

	private String publicId(long bookingId) throws Exception {
		return json(perform(get("/api/bookings/" + bookingId), staff, null)).get("booking").get("publicId").asString();
	}

	/** A partner profile filled in, offering the service, working Monday mornings and evenings, submitted. */
	private long submittedPartner(NexHomeUser partnerUser) throws Exception {
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
		return partnerId;
	}

	/** As {@link #submittedPartner}, then APPROVED directly in the database. */
	private long approvedPartner(NexHomeUser partnerUser) throws Exception {
		long partnerId = submittedPartner(partnerUser);
		entityManager.createNativeQuery("update nexhome_partner_profiles set onboarding_status = 'APPROVED', "
				+ "approval_status = 'VERIFIED', approved_by = 1, approved_at = now() where id = :id")
				.setParameter("id", partnerId).executeUpdate();
		entityManager.clear();
		return partnerId;
	}

	/** Uploads a small PDF as the caller's PARTNER_DOCUMENT file and returns its id (documents need one). */
	private long documentFile(String accessToken) throws Exception {
		byte[] pdf = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
		return json(mockMvc.perform(multipart("/api/files/upload")
				.file(new MockMultipartFile("file", "id.pdf", "application/pdf", pdf))
				.param("purpose", "PARTNER_DOCUMENT")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isCreated())).get("fileId").asLong();
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
