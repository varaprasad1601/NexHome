package com.nexhome.app.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
 * Module 5 end to end against the dev database (rolled back): finding free partners, time off, and who may do
 * what (slot holds are not used for now). Staff calls use a super user, so the dev database's grants do not matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class NexHomeAvailabilityFlowTest {

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
	void partnersAreFoundAndTakeTimeOff() throws Exception {
		String staff = accessToken(newUser("SUPER_USER").getId());
		NexHomeUser partnerUser = newUser("PARTNER");
		String partnerToken = accessToken(partnerUser.getId());
		String otherPartner = accessToken(newUser("PARTNER").getId());
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		long categoryId = json(perform(post("/api/catalog/categories/create"), staff,
				"{\"name\": \"Availability %s\"}".formatted(suffix))
				.andExpect(status().isCreated())).get("serviceCategoryId").asLong();
		long serviceId = json(perform(post("/api/catalog/services/create"), staff, """
				{"categoryId": %d, "name": "Fan Repair %s", "serviceTarget": "APPLIANCE", "pricingType": "FIXED",
				 "basePrice": 299, "minimumCharge": 0, "estimatedDurationMinutes": 60,
				 "platformCommissionType": "PERCENTAGE", "platformCommissionValue": 15, "isActive": true}"""
				.formatted(categoryId, suffix))
				.andExpect(status().isCreated())).get("serviceId").asLong();

		// An approved partner who works Monday mornings and evenings and offers the service
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
				"{\"partnerProfileId\": %d, \"serviceId\": %d, \"experienceYears\": 4}".formatted(partnerId, serviceId))
				.andExpect(status().isCreated());
		LocalDate monday = LocalDate.now(IST).plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
		String start = at(monday, 9, 30);
		String end = at(monday, 11, 0);

		// Not approved yet: not offered
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId, start, end)),
				staff, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].partnerProfileId", not(hasItem((int) partnerId))));
		perform(put("/api/partners/profiles/submit"), staff, "{\"partnerProfileId\": %d}".formatted(partnerId))
				.andExpect(status().isOk());
		entityManager.createNativeQuery("update nexhome_partner_profiles set onboarding_status = 'APPROVED', "
				+ "approval_status = 'VERIFIED', approved_by = 1, approved_at = now() where id = :id")
				.setParameter("id", partnerId).executeUpdate();
		entityManager.clear();

		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId, start, end)),
				staff, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].partnerProfileId", hasItem((int) partnerId)));
		// The lunch break is not working time
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId,
				at(monday, 11, 30), at(monday, 13, 30))), staff, null)
				.andExpect(jsonPath("$[*].partnerProfileId", not(hasItem((int) partnerId))));

		// Time off takes the partner out of the free list for its period
		String offStart = monday.atStartOfDay(IST).toInstant().toString();
		String offEnd = monday.plusDays(1).atStartOfDay(IST).toInstant().toString();
		long timeOffId = json(perform(post("/api/availability/time-off/add"), partnerToken, """
				{"partnerProfileId": %d, "startsAt": "%s", "endsAt": "%s", "reason": "Festival"}"""
				.formatted(partnerId, offStart, offEnd))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reason").value("Festival"))).get("timeOffId").asLong();
		perform(post("/api/availability/time-off/add"), partnerToken, """
				{"partnerProfileId": %d, "startsAt": "%s", "endsAt": "%s"}""".formatted(partnerId, start, end))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("The partner already has time off in this period."));
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId, start, end)),
				staff, null)
				.andExpect(jsonPath("$[*].partnerProfileId", not(hasItem((int) partnerId))));
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s&includeBusy=true".formatted(
				serviceId, start, end)), staff, null)
				.andExpect(jsonPath("$[?(@.partnerProfileId == %d)].busyReason".formatted(partnerId))
						.value("TIME_OFF"));
		perform(get("/api/availability/time-off/%d?upcoming=true".formatted(partnerId)), partnerToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].timeOffId").value(timeOffId));

		// Others' time off needs PARTNER_AVAILABILITY_MANAGE; finding partners is for staff
		perform(get("/api/availability/time-off/" + partnerId), otherPartner, null).andExpect(status().isForbidden());
		perform(put("/api/availability/time-off/cancel"), otherPartner, "{\"timeOffId\": %d}".formatted(timeOffId))
				.andExpect(status().isForbidden());
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId, start, end)),
				partnerToken, null)
				.andExpect(status().isForbidden());
		perform(get("/api/availability/time-off/" + partnerId), null, null).andExpect(status().isUnauthorized());

		// Cancelled, not deleted: kept with when and why, and the partner is free again
		perform(put("/api/availability/time-off/cancel"), partnerToken,
				"{\"timeOffId\": %d, \"reason\": \"Plans changed\"}".formatted(timeOffId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cancelledAt").isNotEmpty())
				.andExpect(jsonPath("$.cancellationReason").value("Plans changed"));
		perform(put("/api/availability/time-off/cancel"), partnerToken, "{\"timeOffId\": %d}".formatted(timeOffId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The time off is already cancelled."));
		perform(put("/api/availability/time-off/update"), partnerToken,
				"{\"timeOffId\": %d, \"reason\": \"x\"}".formatted(timeOffId))
				.andExpect(status().isBadRequest());
		perform(get("/api/availability/time-off/" + partnerId), partnerToken, null)
				.andExpect(jsonPath("$[0].timeOffId").value(timeOffId));
		perform(get("/api/availability/partners?serviceId=%d&startsAt=%s&endsAt=%s".formatted(serviceId, start, end)),
				staff, null)
				.andExpect(jsonPath("$[*].partnerProfileId", hasItem((int) partnerId)));
	}

	private static String at(LocalDate date, int hour, int minute) {
		return date.atTime(hour, minute).atZone(IST).toInstant().toString();
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
