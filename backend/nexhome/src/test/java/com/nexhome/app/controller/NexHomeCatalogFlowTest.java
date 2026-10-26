package com.nexhome.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Module 3 end to end through HTTP and the security filters, against the dev database (rolled back): public
 * catalogue browsing versus CATALOG_MANAGE, pricing rules and quotes, FAQs, versioned policies and their acceptance,
 * and the services partners offer. Slugs and policy keys get a random suffix so existing data never collides.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class NexHomeCatalogFlowTest {

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

	private final String suffix = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000, 9_999_999));

	@Test
	void publicSeesActiveCatalogueWithoutCommission() throws Exception {
		String admin = accessToken(newUser("ADMIN").getId());
		String subAdmin = accessToken(newUser("SUB_ADMIN").getId());
		String customer = accessToken(newUser("CUSTOMER").getId());

		JsonNode category = json(perform(post("/api/catalog/categories/create"), admin,
				"{\"name\": \"Home Cleaning " + suffix + "\", \"displayOrder\": 1}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.slug").value("home-cleaning-" + suffix))
				.andExpect(jsonPath("$.isActive").value(true)));
		long categoryId = category.get("serviceCategoryId").asLong();
		assertThat(category.get("publicId").asString()).matches("NHCATG\\d{4,}");
		perform(post("/api/catalog/categories/create"), admin, "{\"name\": \"Home Cleaning " + suffix + "\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Another category has this slug."));

		// The rules of the table's check constraints come back as readable 400s
		perform(post("/api/catalog/services/create"), admin, service(categoryId, "Hourly " + suffix, "HOURLY", 400))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"Hourly pricing needs the minimum billable minutes and the billing increment."));
		perform(post("/api/catalog/services/create"), admin, service(categoryId, "Pct " + suffix, "FIXED", 100)
				.replace("\"platformCommissionValue\": 15", "\"platformCommissionValue\": 120"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("A percentage commission cannot be more than 100."));

		JsonNode service = json(perform(post("/api/catalog/services/create"), admin,
				service(categoryId, "Deep Cleaning " + suffix, "FIXED", 1499))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.platformCommissionType").value("PERCENTAGE")));
		long serviceId = service.get("serviceId").asLong();
		String slug = service.get("slug").asString();
		assertThat(service.get("publicId").asString()).matches("NHSERV\\d{4,}");

		long variantId = json(perform(post("/api/catalog/variants/add"), admin, """
				{"serviceId": %d, "variantCode": "TWO_BHK", "name": "2 BHK", "priceOverride": 1999,
				 "durationMinutes": 240}""".formatted(serviceId))
				.andExpect(status().isCreated())).get("serviceVariantId").asLong();
		perform(post("/api/catalog/variants/add"), admin,
				"{\"serviceId\": %d, \"variantCode\": \"TWO_BHK\", \"name\": \"Again\"}".formatted(serviceId))
				.andExpect(status().isConflict());
		long hiddenVariantId = json(perform(post("/api/catalog/variants/add"), admin,
				"{\"serviceId\": %d, \"variantCode\": \"OLD\", \"name\": \"Old\", \"isActive\": false}"
						.formatted(serviceId))).get("serviceVariantId").asLong();
		perform(post("/api/catalog/media/add"), admin,
				"{\"serviceId\": %d, \"mediaType\": \"IMAGE\", \"storageKey\": \"catalog/deep.jpg\"}".formatted(serviceId))
				.andExpect(status().isCreated());
		perform(post("/api/catalog/requirements/add"), admin, """
				{"serviceId": %d, "requirementType": "PREPARATION", "description": "Keep the area clear"}"""
				.formatted(serviceId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.isMandatory").value(true));

		// Anonymous: active entries only, no commission; staff with CATALOG_MANAGE read see everything
		perform(get("/api/catalog/services/" + serviceId), null, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.service.platformCommissionType").doesNotExist())
				.andExpect(jsonPath("$.variants.length()").value(1))
				.andExpect(jsonPath("$.media.length()").value(1))
				.andExpect(jsonPath("$.requirements[0].requirementType").value("PREPARATION"));
		perform(get("/api/catalog/services/slug/" + slug), customer, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.service.serviceId").value(serviceId));
		perform(get("/api/catalog/services/" + serviceId), subAdmin, null)
				.andExpect(jsonPath("$.service.platformCommissionValue").value(15))
				.andExpect(jsonPath("$.variants.length()").value(2));
		perform(get("/api/catalog/services?categoryId=" + categoryId), null, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].categoryName").value("Home Cleaning " + suffix));

		// Quotes: the variant's price applies, never below the minimum charge
		perform(get("/api/catalog/services/quote?serviceId=" + serviceId + "&serviceVariantId=" + variantId), null, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unitPrice").value(1999))
				.andExpect(jsonPath("$.total").value(1999.00))
				.andExpect(jsonPath("$.durationMinutes").value(240));
		perform(get("/api/catalog/services/quote?serviceId=" + serviceId + "&serviceVariantId=" + hiddenVariantId),
				null, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The variant is not available."));

		// Deactivated: gone for the public, still there for staff
		perform(put("/api/catalog/services/update"), admin, "{\"serviceId\": %d, \"isActive\": false}".formatted(serviceId))
				.andExpect(status().isOk());
		perform(get("/api/catalog/services/" + serviceId), customer, null).andExpect(status().isNotFound());
		perform(get("/api/catalog/services/" + serviceId), admin, null).andExpect(status().isOk());
		perform(get("/api/catalog/services?includeInactive=true"), customer, null).andExpect(status().isForbidden());
		perform(get("/api/catalog/services?includeInactive=true&categoryId=" + categoryId), subAdmin, null)
				.andExpect(jsonPath("$.totalElements").value(1));

		// Changes need CATALOG_MANAGE write; in-use entries are deactivated rather than deleted
		perform(post("/api/catalog/categories/create"), subAdmin, "{\"name\": \"X\"}").andExpect(status().isForbidden());
		perform(post("/api/catalog/categories/create"), null, "{\"name\": \"X\"}").andExpect(status().isUnauthorized());
		perform(delete("/api/catalog/categories/delete/" + categoryId), admin, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(
						"Category is used by services, FAQs or partner profiles; deactivate it instead."));
		perform(delete("/api/catalog/services/delete/" + serviceId), admin, null).andExpect(status().isConflict());
		perform(delete("/api/catalog/variants/delete/" + hiddenVariantId), admin, null)
				.andExpect(status().isNoContent());
	}

	@Test
	void quotesFollowThePricingType() throws Exception {
		String admin = accessToken(newUser("ADMIN").getId());
		long categoryId = json(perform(post("/api/catalog/categories/create"), admin,
				"{\"name\": \"Repairs " + suffix + "\"}")).get("serviceCategoryId").asLong();
		long hourly = json(perform(post("/api/catalog/services/create"), admin,
				service(categoryId, "Electrician " + suffix, "HOURLY", 400)
						.replace("\"minimumCharge\": 0", "\"minimumBillableMinutes\": 60, \"billingIncrementMinutes\": 30, "
								+ "\"minimumCharge\": 0"))
				.andExpect(status().isCreated())).get("serviceId").asLong();
		long perUnit = json(perform(post("/api/catalog/services/create"), admin,
				service(categoryId, "Fan Install " + suffix, "PER_UNIT", 250)
						.replace("\"minimumCharge\": 0", "\"minimumCharge\": 600")))
				.get("serviceId").asLong();

		// 70 minutes: 60 minimum plus one 30-minute increment = 90 minutes at 400/hour
		perform(get("/api/catalog/services/quote?serviceId=" + hourly + "&minutes=70"), null, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.billableMinutes").value(90))
				.andExpect(jsonPath("$.total").value(600.00));
		perform(get("/api/catalog/services/quote?serviceId=" + hourly + "&minutes=20"), null, null)
				.andExpect(jsonPath("$.billableMinutes").value(60))
				.andExpect(jsonPath("$.subtotal").value(400.00));
		// Without minutes the estimated duration (120) is priced
		perform(get("/api/catalog/services/quote?serviceId=" + hourly), null, null)
				.andExpect(jsonPath("$.billableMinutes").value(120))
				.andExpect(jsonPath("$.total").value(800.00));
		perform(get("/api/catalog/services/quote?serviceId=" + hourly + "&minutes=0"), null, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Minutes must be more than 0 to price an hourly service."));
		// Two fans cost 500, raised to the 600 minimum charge; four cost 1000
		perform(get("/api/catalog/services/quote?serviceId=" + perUnit + "&quantity=2"), null, null)
				.andExpect(jsonPath("$.subtotal").value(500.00))
				.andExpect(jsonPath("$.total").value(600));
		perform(get("/api/catalog/services/quote?serviceId=" + perUnit + "&quantity=4"), null, null)
				.andExpect(jsonPath("$.total").value(1000.00));

		long otherVariant = json(perform(post("/api/catalog/variants/add"), admin,
				"{\"serviceId\": %d, \"variantCode\": \"CEILING\", \"name\": \"Ceiling fan\"}".formatted(perUnit)))
				.get("serviceVariantId").asLong();
		perform(get("/api/catalog/services/quote?serviceId=" + hourly + "&serviceVariantId=" + otherVariant), null, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The variant does not belong to this service."));
	}

	@Test
	void faqsArePublicOncePublished() throws Exception {
		String admin = accessToken(newUser("ADMIN").getId());
		long categoryId = json(perform(post("/api/catalog/categories/create"), admin,
				"{\"name\": \"Painting " + suffix + "\"}")).get("serviceCategoryId").asLong();
		String question = "How long does painting take " + suffix + "?";

		perform(post("/api/faqs/create"), admin, """
				{"question": "%s", "answer": "Two days.", "categoryId": %d, "audience": "CUSTOMER", "isPublished": true}"""
				.formatted(question, categoryId))
				.andExpect(status().isCreated());
		perform(post("/api/faqs/create"), admin, """
				{"question": "Draft %s", "answer": "Later.", "categoryId": %d, "audience": "BOTH"}"""
				.formatted(suffix, categoryId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.isPublished").value(false));
		perform(post("/api/faqs/create"), admin, """
				{"question": "Partner %s", "answer": "Yes.", "categoryId": %d, "audience": "PARTNER", "isPublished": true}"""
				.formatted(suffix, categoryId))
				.andExpect(status().isCreated());
		perform(post("/api/faqs/create"), admin,
				"{\"question\": \"Q\", \"answer\": \"A\", \"categoryId\": 1, \"serviceId\": 1, \"audience\": \"BOTH\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("An FAQ belongs to a category or a service, not both."));

		perform(get("/api/faqs?audience=CUSTOMER&categoryId=" + categoryId), null, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].question").value(question));
		perform(get("/api/faqs/all?categoryId=" + categoryId), admin, null)
				.andExpect(jsonPath("$.totalElements").value(3));

		// FAQs of a deactivated category are no longer public
		perform(put("/api/catalog/categories/update"), admin,
				"{\"serviceCategoryId\": %d, \"isActive\": false}".formatted(categoryId))
				.andExpect(status().isOk());
		perform(get("/api/faqs?categoryId=" + categoryId), null, null)
				.andExpect(jsonPath("$.length()").value(0));
		perform(get("/api/faqs/all"), accessToken(newUser("CUSTOMER").getId()), null).andExpect(status().isForbidden());
		perform(post("/api/faqs/create"), accessToken(newUser("SUB_ADMIN").getId()),
				"{\"question\": \"Q\", \"answer\": \"A\", \"audience\": \"BOTH\"}")
				.andExpect(status().isForbidden());
	}

	@Test
	void customersAcceptTheCurrentPolicyVersion() throws Exception {
		String admin = accessToken(newUser("ADMIN").getId());
		NexHomeUser customer = newUser("CUSTOMER");
		String customerToken = accessToken(customer.getId());
		String partner = accessToken(newUser("PARTNER").getId());
		String key = "TERMS_" + suffix;

		long current = json(perform(post("/api/policies/create"), admin, policy(key, "1.0", "2026-01-01T00:00:00+05:30"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.isPublished").value(false))).get("policyDocumentId").asLong();
		perform(post("/api/policies/create"), admin, policy(key, "1.0", "2026-01-01T00:00:00Z"))
				.andExpect(status().isConflict());
		// Drafts are not public
		perform(get("/api/policies/current/" + key), null, null).andExpect(status().isNotFound());

		perform(put("/api/policies/publish"), admin, "{\"policyDocumentId\": %d}".formatted(current))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.publishedAt").isNotEmpty());
		perform(put("/api/policies/update"), admin, "{\"policyDocumentId\": %d, \"title\": \"New\"}".formatted(current))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"A published policy cannot be changed; create a new version instead."));
		perform(delete("/api/policies/delete/" + current), admin, null).andExpect(status().isBadRequest());

		// A published version that starts in the future is not current yet
		long future = json(perform(post("/api/policies/create"), admin,
				policy(key, "2.0", Instant.now().plusSeconds(86_400).toString()))).get("policyDocumentId").asLong();
		perform(put("/api/policies/publish"), admin, "{\"policyDocumentId\": %d}".formatted(future))
				.andExpect(status().isOk());
		perform(get("/api/policies/current/" + key), null, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value("1.0"));

		perform(get("/api/policies/pending"), customerToken, null)
				.andExpect(jsonPath("$[*].policyKey").value(hasItem(key)));
		perform(get("/api/policies/pending"), partner, null)
				.andExpect(jsonPath("$[*].policyKey").value(not(hasItem(key))));

		perform(post("/api/policies/accept"), customerToken,
				"{\"policyDocumentId\": %d, \"acceptanceMethod\": \"APP_CHECKBOX\"}".formatted(future))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Only the current version of a policy can be accepted."));
		perform(post("/api/policies/accept"), customerToken,
				"{\"policyDocumentId\": %d, \"acceptanceMethod\": \"APP_CHECKBOX\"}".formatted(current))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.policyKey").value(key))
				.andExpect(jsonPath("$.ipAddress").value("127.0.0.1"));
		perform(post("/api/policies/accept"), customerToken,
				"{\"policyDocumentId\": %d, \"acceptanceMethod\": \"APP_CHECKBOX\"}".formatted(current))
				.andExpect(status().isConflict());
		perform(get("/api/policies/pending"), customerToken, null)
				.andExpect(jsonPath("$[*].policyKey").value(not(hasItem(key))));

		perform(get("/api/policies/acceptances/me"), customerToken, null)
				.andExpect(jsonPath("$[0].version").value("1.0"));
		perform(get("/api/policies/acceptances/users/" + customer.getId()), partner, null)
				.andExpect(status().isForbidden());
		perform(get("/api/policies/acceptances/users/" + customer.getId()), admin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		perform(get("/api/policies/all?policyKey=" + key), accessToken(newUser("SUB_ADMIN").getId()), null)
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void partnersPickActiveServicesAndAMainCategory() throws Exception {
		String admin = accessToken(newUser("ADMIN").getId());
		NexHomeUser partner = newUser("PARTNER");
		String partnerToken = accessToken(partner.getId());
		long categoryId = json(perform(post("/api/catalog/categories/create"), admin,
				"{\"name\": \"Plumbing " + suffix + "\"}")).get("serviceCategoryId").asLong();
		long inactiveCategory = json(perform(post("/api/catalog/categories/create"), admin,
				"{\"name\": \"Old " + suffix + "\", \"isActive\": false}")).get("serviceCategoryId").asLong();
		long tap = json(perform(post("/api/catalog/services/create"), admin,
				service(categoryId, "Tap Repair " + suffix, "FIXED", 199))).get("serviceId").asLong();
		long pipe = json(perform(post("/api/catalog/services/create"), admin,
				service(categoryId, "Pipe Fitting " + suffix, "FIXED", 499))).get("serviceId").asLong();
		long retired = json(perform(post("/api/catalog/services/create"), admin,
				service(categoryId, "Retired " + suffix, "FIXED", 99).replace("\"isActive\": true",
						"\"isActive\": false"))).get("serviceId").asLong();

		perform(post("/api/partners/profiles/create"), partnerToken, """
				{"userId": %d, "firstName": "Suresh", "lastName": "Babu", "mainServiceCategoryId": %d}"""
				.formatted(partner.getId(), inactiveCategory))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Main service category must be an active service category."));
		long profileId = json(perform(post("/api/partners/profiles/create"), partnerToken, """
				{"userId": %d, "firstName": "Suresh", "lastName": "Babu", "mainServiceCategoryId": %d}"""
				.formatted(partner.getId(), categoryId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mainServiceCategoryId").value(categoryId))).get("partnerProfileId").asLong();

		perform(post("/api/partners/services/add"), partnerToken,
				"{\"partnerProfileId\": %d, \"serviceId\": %d, \"experienceYears\": 4}".formatted(profileId, tap))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.isPrimaryService").value(true))
				.andExpect(jsonPath("$.serviceName").value("Tap Repair " + suffix));
		perform(post("/api/partners/services/add"), partnerToken,
				"{\"partnerProfileId\": %d, \"serviceId\": %d}".formatted(profileId, tap))
				.andExpect(status().isConflict());
		perform(post("/api/partners/services/add"), partnerToken,
				"{\"partnerProfileId\": %d, \"serviceId\": %d}".formatted(profileId, retired))
				.andExpect(status().isNotFound());
		perform(post("/api/partners/services/add"), partnerToken,
				"{\"partnerProfileId\": %d, \"serviceId\": %d, \"isPrimaryService\": true}".formatted(profileId, pipe))
				.andExpect(status().isCreated());
		perform(get("/api/partners/profiles/services/" + profileId), partnerToken, null)
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].serviceId").value(pipe))
				.andExpect(jsonPath("$[1].isPrimaryService").value(false));
		perform(get("/api/partners/profiles/services/" + profileId), accessToken(newUser("PARTNER").getId()), null)
				.andExpect(status().isForbidden());

		// The main category is part of what submission needs
		perform(delete("/api/catalog/categories/delete/" + categoryId), admin, null).andExpect(status().isConflict());
	}

	private static String service(long categoryId, String name, String pricingType, int basePrice) {
		return """
				{"categoryId": %d, "name": "%s", "pricingType": "%s", "basePrice": %d, "minimumCharge": 0,
				 "estimatedDurationMinutes": 120, "platformCommissionType": "PERCENTAGE", "platformCommissionValue": 15,
				 "isActive": true}""".formatted(categoryId, name, pricingType, basePrice);
	}

	private static String policy(String key, String version, String effectiveFrom) {
		return """
				{"policyKey": "%s", "title": "Terms of Service", "audience": "CUSTOMER", "version": "%s",
				 "content": "You agree to ...", "effectiveFrom": "%s"}""".formatted(key, version, effectiveFrom);
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
