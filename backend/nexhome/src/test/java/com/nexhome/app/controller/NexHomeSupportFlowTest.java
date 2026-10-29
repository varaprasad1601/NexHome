package com.nexhome.app.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Module 9 end to end against the dev database (rolled back): a completed booking reviewed by its customer (once,
 * the partner's and customer's ratings, hiding), the notifications it sends, a support ticket from opening to closing
 * (assignment, replies, internal notes hidden from the requester, resolving and reopening), staff sending a
 * notification, and the audit log. Staff calls use a super user, so the dev database's grants do not matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class NexHomeSupportFlowTest {

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
	private EntityManager entityManager;

	@Test
	void aCompletedBookingIsReviewedAndASupportTicketIsSeenThrough() throws Exception {
		NexHomeUser staffUser = newUser("SUPER_USER");
		String staff = accessToken(staffUser.getId());
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		long categoryId = json(perform(post("/api/catalog/categories/create"), staff,
				"{\"name\": \"Support %s\"}".formatted(suffix))
				.andExpect(status().isCreated())).get("serviceCategoryId").asLong();
		long serviceId = json(perform(post("/api/catalog/services/create"), staff, """
				{"categoryId": %d, "name": "Tap Repair %s", "serviceTarget": "APPLIANCE", "pricingType": "FIXED",
				 "basePrice": 300, "minimumCharge": 0, "estimatedDurationMinutes": 60,
				 "platformCommissionType": "PERCENTAGE", "platformCommissionValue": 10, "isActive": true}"""
				.formatted(categoryId, suffix))
				.andExpect(status().isCreated())).get("serviceId").asLong();
		NexHomeUser customerUser = newUser("CUSTOMER");
		String customer = accessToken(customerUser.getId());
		String otherCustomer = accessToken(newUser("CUSTOMER").getId());
		long customerId = json(perform(post("/api/profiles/customers/create"), customer, """
				{"userId": %d, "firstName": "Meena", "lastName": "Rao"}""".formatted(customerUser.getId()))
				.andExpect(status().isCreated())).get("customerProfileId").asLong();
		long addressId = json(perform(post("/api/addresses/create"), customer, """
				{"userId": %d, "contactName": "Meena", "mobileNumber": "9876543210", "addressType": "HOME",
				 "label": "Home", "addressLine1": "Plot 7", "city": "Hyderabad", "state": "Telangana",
				 "postalCode": "500081"}""".formatted(customerUser.getId()))
				.andExpect(status().isCreated())).get("addressId").asLong();
		NexHomeUser partnerUser = newUser("PARTNER");
		String partner = accessToken(partnerUser.getId());
		long partnerId = approvedPartner(staff, partnerUser, categoryId, serviceId);
		LocalDate monday = LocalDate.now(IST).plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

		// A cash booking, confirmed, assigned and done
		long bookingId = json(perform(post("/api/bookings/create"), staff, """
				{"customerProfileId": %d, "serviceId": %d, "addressId": %d, "bookingType": "SCHEDULED",
				 "scheduledStartAt": "%s", "paymentMethod": "CASH"}""".formatted(customerId, serviceId, addressId,
				monday.atTime(10, 0).atZone(IST).toInstant()))
				.andExpect(status().isCreated())).get("bookingId").asLong();
		perform(post("/api/reviews/create"), customer,
				"{\"bookingId\": %d, \"serviceRating\": 4, \"partnerRating\": 2}".formatted(bookingId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Only a completed booking can be reviewed."));
		perform(put("/api/bookings/status/update"), staff,
				"{\"bookingId\": %d, \"bookingStatus\": \"CONFIRMED\"}".formatted(bookingId))
				.andExpect(status().isOk());
		perform(post("/api/bookings/assignments/offer"), staff,
				"{\"bookingId\": %d, \"partnerProfileId\": %d, \"direct\": true}".formatted(bookingId, partnerId))
				.andExpect(status().isCreated());
		// The partner hears of the job
		perform(get("/api/notifications/me"), partner, null)
				.andExpect(jsonPath("$.content[0].referenceType").value("BOOKING"))
				.andExpect(jsonPath("$.content[0].referenceId").value(bookingId));
		for (String event : List.of("START", "STOP")) {
			perform(post("/api/bookings/timer"), partner,
					"{\"bookingId\": %d, \"eventType\": \"%s\"}".formatted(bookingId, event))
					.andExpect(status().isCreated());
		}

		// The customer reviews it once; others cannot
		perform(post("/api/reviews/create"), otherCustomer,
				"{\"bookingId\": %d, \"serviceRating\": 4, \"partnerRating\": 2}".formatted(bookingId))
				.andExpect(status().isForbidden());
		perform(post("/api/reviews/create"), customer,
				"{\"bookingId\": %d, \"serviceRating\": 6, \"partnerRating\": 2}".formatted(bookingId))
				.andExpect(status().isBadRequest());
		long reviewId = json(perform(post("/api/reviews/create"), customer,
				"{\"bookingId\": %d, \"serviceRating\": 4, \"partnerRating\": 2, \"reviewText\": \" Late \"}"
						.formatted(bookingId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reviewStatus").value("PUBLISHED"))
				.andExpect(jsonPath("$.reviewText").value("Late"))).get("reviewId").asLong();
		perform(post("/api/reviews/create"), customer,
				"{\"bookingId\": %d, \"serviceRating\": 5, \"partnerRating\": 5}".formatted(bookingId))
				.andExpect(status().isConflict());
		perform(get("/api/reviews/bookings/" + bookingId), partner, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.partnerRating").value(2));
		perform(get("/api/reviews/bookings/" + bookingId), otherCustomer, null).andExpect(status().isForbidden());
		perform(get("/api/reviews"), customer, null).andExpect(status().isForbidden());
		// The ratings reach the statistics
		perform(get("/api/bookings/partner-stats/" + partnerId), staff, null)
				.andExpect(jsonPath("$.averageRating").value(2.0));
		perform(get("/api/bookings/customer-stats/" + customerId), staff, null)
				.andExpect(jsonPath("$.averageRating").value(2.0));
		perform(get("/api/reviews/summary?partnerProfileId=" + partnerId), partner, null)
				.andExpect(jsonPath("$.averageRating").value(2.0))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.ratingCounts.2").value(1));
		perform(get("/api/reviews/summary?partnerProfileId=" + partnerId), otherCustomer, null)
				.andExpect(status().isForbidden());
		// The partner hears of the review; a low rating tells the staff who read reviews
		perform(get("/api/notifications/me"), partner, null)
				.andExpect(jsonPath("$.content[0].notificationType").value("REVIEW"));
		JsonNode staffNotices = json(perform(get("/api/notifications/me?notificationType=REVIEW"), staff, null)
				.andExpect(jsonPath("$.content[0].title").value(Matchers.startsWith("Low rating"))));
		perform(put("/api/notifications/me/read"), staff, "{\"notificationIds\": [%d]}".formatted(
				staffNotices.get("content").get(0).get("notificationId").asLong()))
				.andExpect(jsonPath("$.updated").value(1));
		perform(get("/api/notifications/me?unread=true&notificationType=REVIEW"), staff, null)
				.andExpect(jsonPath("$.totalElements").value(0));
		// Hidden, it no longer counts
		perform(put("/api/reviews/status/update"), customer,
				"{\"reviewId\": %d, \"reviewStatus\": \"HIDDEN\"}".formatted(reviewId))
				.andExpect(status().isForbidden());
		perform(put("/api/reviews/status/update"), staff,
				"{\"reviewId\": %d, \"reviewStatus\": \"HIDDEN\"}".formatted(reviewId))
				.andExpect(status().isOk());
		perform(get("/api/bookings/partner-stats/" + partnerId), staff, null)
				.andExpect(jsonPath("$.averageRating").doesNotExist());

		// A support ticket about the booking
		perform(post("/api/support/tickets/create"), otherCustomer, """
				{"bookingId": %d, "category": "SERVICE_QUALITY", "subject": "Not mine", "description": "x"}"""
				.formatted(bookingId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The booking is not yours."));
		JsonNode ticket = json(perform(post("/api/support/tickets/create"), customer, """
				{"bookingId": %d, "category": "SERVICE_QUALITY", "subject": "Partner was late",
				 "description": "The partner came an hour late."}""".formatted(bookingId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.publicId").value(Matchers.startsWith("NHTICK")))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.priority").value("MEDIUM"))
				.andExpect(jsonPath("$.bookingId").value(bookingId)));
		long ticketId = ticket.get("supportTicketId").asLong();
		String ticketPublicId = ticket.get("publicId").asString();
		perform(get("/api/support/tickets/" + ticketId), otherCustomer, null).andExpect(status().isForbidden());
		perform(put("/api/support/tickets/update"), customer,
				"{\"supportTicketId\": %d, \"status\": \"RESOLVED\"}".formatted(ticketId))
				.andExpect(status().isForbidden());
		// Assigning starts it; staff answer, and note something for staff only
		perform(put("/api/support/tickets/update"), staff, "{\"supportTicketId\": %d, \"assignedToUserId\": %d}"
				.formatted(ticketId, customerUser.getId()))
				.andExpect(status().isBadRequest());
		perform(put("/api/support/tickets/update"), staff, "{\"supportTicketId\": %d, \"assignedToUserId\": %d}"
				.formatted(ticketId, staffUser.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.assignedToUserId").value(staffUser.getId()));
		perform(post("/api/support/tickets/messages/add"), staff,
				"{\"supportTicketId\": %d, \"message\": \"Sorry, we will look into it.\"}".formatted(ticketId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fromStaff").value(true));
		perform(post("/api/support/tickets/messages/add"), staff, """
				{"supportTicketId": %d, "message": "Partner had a flat tyre.", "isInternalNote": true}"""
				.formatted(ticketId))
				.andExpect(status().isCreated());
		perform(post("/api/support/tickets/messages/add"), customer, """
				{"supportTicketId": %d, "message": "A note", "isInternalNote": true}""".formatted(ticketId))
				.andExpect(status().isForbidden());
		perform(get("/api/support/tickets/" + ticketId), customer, null)
				.andExpect(jsonPath("$.ticket.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.messages.length()").value(1));
		perform(get("/api/support/tickets/" + ticketId), staff, null)
				.andExpect(jsonPath("$.messages.length()").value(2))
				.andExpect(jsonPath("$.messages[1].isInternalNote").value(true));
		// Resolved, then the customer writes again: back in progress; closed, no more replies
		perform(put("/api/support/tickets/update"), staff,
				"{\"supportTicketId\": %d, \"status\": \"RESOLVED\"}".formatted(ticketId))
				.andExpect(jsonPath("$.status").value("RESOLVED"))
				.andExpect(jsonPath("$.resolvedAt").isNotEmpty());
		perform(post("/api/support/tickets/messages/add"), customer,
				"{\"supportTicketId\": %d, \"message\": \"It happened again.\"}".formatted(ticketId))
				.andExpect(status().isCreated());
		perform(get("/api/support/tickets/" + ticketId), customer, null)
				.andExpect(jsonPath("$.ticket.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.ticket.resolvedAt").doesNotExist());
		perform(put("/api/support/tickets/update"), staff,
				"{\"supportTicketId\": %d, \"status\": \"CLOSED\"}".formatted(ticketId))
				.andExpect(jsonPath("$.closedAt").isNotEmpty());
		perform(post("/api/support/tickets/messages/add"), customer,
				"{\"supportTicketId\": %d, \"message\": \"Hello?\"}".formatted(ticketId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The ticket is closed; open a new one."));
		perform(put("/api/support/tickets/update"), staff,
				"{\"supportTicketId\": %d, \"status\": \"OPEN\"}".formatted(ticketId))
				.andExpect(status().isBadRequest());
		perform(get("/api/support/tickets/me"), customer, null)
				.andExpect(jsonPath("$.content[0].supportTicketId").value(ticketId));
		perform(get("/api/support/tickets?bookingId=" + bookingId), staff, null)
				.andExpect(jsonPath("$.totalElements").value(1));
		// The customer heard of the replies and status changes
		perform(get("/api/notifications/me/summary"), customer, null)
				.andExpect(jsonPath("$.unreadByType.SUPPORT").value(Matchers.greaterThanOrEqualTo(3)));

		// Staff send a notification; the audit log has the ticket's changes, for staff only
		perform(post("/api/notifications/send"), staff, """
				{"audience": "USER", "userId": %d, "title": "Thanks", "message": "Sorry about the delay."}"""
				.formatted(customerUser.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.recipients").value(1));
		perform(post("/api/notifications/send"), staff,
				"{\"audience\": \"EVERYONE\", \"title\": \"x\", \"message\": \"y\"}")
				.andExpect(status().isBadRequest());
		perform(post("/api/notifications/send"), customer,
				"{\"audience\": \"STAFF\", \"title\": \"x\", \"message\": \"y\"}")
				.andExpect(status().isForbidden());
		perform(get("/api/audit-logs?entityType=SUPPORT_TICKET&entityId=" + ticketPublicId), staff, null)
				.andExpect(status().isOk())
				// Assigned, resolved and closed (replies that move the status are not audited)
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].newValues.status").value("CLOSED"))
				.andExpect(jsonPath("$.content[0].actorUserId").value(staffUser.getId()));
		perform(get("/api/audit-logs"), customer, null).andExpect(status().isForbidden());
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
