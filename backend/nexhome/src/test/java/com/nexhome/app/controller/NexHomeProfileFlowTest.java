package com.nexhome.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
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
import com.nexhome.app.model.NexHomeServiceCategory;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAuthRefreshTokenRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeServiceCategoryRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Module 2 end to end through HTTP and the security filters, against the dev database (rolled back): customer and
 * staff profiles, addresses, and partner onboarding with documents and bank accounts. Covers who may do what (owner,
 * CUSTOMER_MANAGE / ADMIN_MANAGE / SUB_ADMIN_MANAGE / PARTNER_ONBOARDING_MANAGE, nobody on their own review) and the stored encryption.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class NexHomeProfileFlowTest {

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
	private NexHomeServiceCategoryRepository categoryRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void customersManageTheirProfile() throws Exception {
		NexHomeUser customer = newUser("CUSTOMER");
		String customerToken = accessToken(customer.getId());
		String otherCustomer = accessToken(newUser("CUSTOMER").getId());
		String subAdmin = accessToken(newUser("SUB_ADMIN").getId());
		String admin = accessToken(newUser("ADMIN").getId());

		JsonNode created = json(perform(post("/api/profiles/customers/create"), customerToken, """
				{"userId": %d, "firstName": "  Ravi   Teja ", "lastName": "Kumar", "dateOfBirth": "1994-03-12",
				 "preferredLanguage": "te"}""".formatted(customer.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.firstName").value("Ravi Teja"))
				.andExpect(jsonPath("$.preferredLanguage").value("te"))
				.andExpect(jsonPath("$.marketingConsent").value(false))
				.andExpect(jsonPath("$.creationDate").isNotEmpty()));
		long profileId = created.get("customerProfileId").asLong();
		String publicId = created.get("publicId").asString();
		assertThat(publicId).matches("NHCUST\\d{4,}");

		perform(post("/api/profiles/customers/create"), customerToken,
				"{\"userId\": %d, \"firstName\": \"Ravi\", \"lastName\": \"Kumar\"}".formatted(customer.getId()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This account already has a customer profile."));

		// Someone else's profile needs CUSTOMER_MANAGE
		perform(get("/api/profiles/customers/" + profileId), otherCustomer, null).andExpect(status().isForbidden());
		perform(put("/api/profiles/customers/update"), otherCustomer,
				"{\"customerProfileId\": %d, \"lastName\": \"X\"}".formatted(profileId))
				.andExpect(status().isForbidden());
		perform(get("/api/profiles/customers/" + profileId), subAdmin, null).andExpect(status().isOk());

		// Partial update: a missing field keeps its value, an empty optional one is cleared
		perform(put("/api/profiles/customers/update"), customerToken, """
				{"customerProfileId": %d, "lastName": "Varma", "preferredLanguage": ""}""".formatted(profileId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Ravi Teja"))
				.andExpect(jsonPath("$.lastName").value("Varma"))
				.andExpect(jsonPath("$.preferredLanguage").doesNotExist());

		// The console's customer list shows the profile and finds it by name or public id; counts by status
		perform(get("/api/users/customers?search=ravi teja varma"), subAdmin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.userId == %d)].publicId".formatted(customer.getId())).value(publicId))
				.andExpect(jsonPath("$.content[?(@.userId == %d)].firstName".formatted(customer.getId())).value("Ravi Teja"));
		perform(get("/api/users/customers?search=" + publicId.toLowerCase()), subAdmin, null)
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].customerProfileId").value(profileId))
				.andExpect(jsonPath("$.content[0].roles[0].roleCode").value("CUSTOMER"));
		perform(get("/api/users/customers/stats"), subAdmin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
		perform(get("/api/users/customers/stats"), otherCustomer, null).andExpect(status().isForbidden());
		perform(put("/api/profiles/customers/update"), customerToken,
				"{\"customerProfileId\": %d, \"userId\": 1}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("User cannot be updated"));
		perform(put("/api/profiles/customers/update"), customerToken,
				"{\"customerProfileId\": %d, \"preferredLanguage\": \"xx\"}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Preferred language must be one of: en, hi, te")));
		perform(get("/api/profiles/customers/me"), customerToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customerProfileId").value(profileId));

		// Only customers get customer profiles
		NexHomeUser partner = newUser("PARTNER");
		perform(post("/api/profiles/customers/create"), accessToken(partner.getId()),
				"{\"userId\": %d, \"firstName\": \"Ravi\", \"lastName\": \"Kumar\"}".formatted(partner.getId()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Customer profiles are only for accounts with the CUSTOMER role."));

		// The list is for CUSTOMER_MANAGE read (SUPER_USER, ADMIN, SUB_ADMIN)
		for (String token : List.of(subAdmin, admin)) {
			perform(get("/api/profiles/customers?search=" + publicId.toLowerCase()), token, null)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(1))
					.andExpect(jsonPath("$.content[0].publicId").value(publicId));
		}
		perform(get("/api/profiles/customers"), customerToken, null).andExpect(status().isForbidden());
	}

	@Test
	void customersManageTheirAddresses() throws Exception {
		NexHomeUser customer = newUser("CUSTOMER");
		String customerToken = accessToken(customer.getId());
		String otherCustomer = accessToken(newUser("CUSTOMER").getId());

		JsonNode first = json(perform(post("/api/addresses/create"), customerToken, address(customer.getId(),
				"\"postalCode\": \"500081\", \"latitude\": 17.385044123, \"longitude\": 78.486671"))
				.andExpect(status().isCreated())
				// The first address becomes the default
				.andExpect(jsonPath("$.isDefault").value(true))
				.andExpect(jsonPath("$.latitude").value(17.3850441))
				.andExpect(jsonPath("$.countryCode").value("IN")));
		assertThat(first.get("publicId").asString()).matches("NHADDR\\d{4,}");
		long firstId = first.get("addressId").asLong();

		long secondId = json(perform(post("/api/addresses/create"), customerToken, address(customer.getId(),
				"\"postalCode\": \"500032\", \"isDefault\": true"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.isDefault").value(true))).get("addressId").asLong();
		perform(get("/api/addresses/me"), customerToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].addressId").value(secondId))
				.andExpect(jsonPath("$[1].addressId").value(firstId))
				.andExpect(jsonPath("$[1].isDefault").value(false));

		perform(post("/api/addresses/create"), customerToken, address(customer.getId(), "\"postalCode\": \"12345\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Postal code must be a 6-digit PIN code for India."));
		perform(post("/api/addresses/create"), customerToken, address(customer.getId(),
				"\"postalCode\": \"94105\", \"countryCode\": \"us\""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.countryCode").value("US"))
				.andExpect(jsonPath("$.isDefault").value(false));
		perform(post("/api/addresses/create"), customerToken, address(customer.getId(),
				"\"postalCode\": \"500081\", \"latitude\": 17.4"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Latitude and longitude must be given together."));

		// Making the first default again moves the flag back
		perform(put("/api/addresses/update"), customerToken,
				"{\"addressId\": %d, \"isDefault\": true, \"label\": \"\"}".formatted(firstId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isDefault").value(true))
				.andExpect(jsonPath("$.label").doesNotExist());
		perform(get("/api/addresses/" + secondId), customerToken, null)
				.andExpect(jsonPath("$.isDefault").value(false));

		// Other customers cannot see or change it; staff with CUSTOMER_MANAGE can
		perform(get("/api/addresses/" + firstId), otherCustomer, null).andExpect(status().isForbidden());
		perform(get("/api/addresses/users/" + customer.getId()), otherCustomer, null).andExpect(status().isForbidden());
		perform(delete("/api/addresses/delete/" + firstId), otherCustomer, null).andExpect(status().isForbidden());
		perform(get("/api/addresses/users/" + customer.getId()), accessToken(newUser("SUB_ADMIN").getId()), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));
		perform(post("/api/addresses/create"), accessToken(newUser("SUB_ADMIN").getId()),
				address(customer.getId(), "\"postalCode\": \"500081\""))
				.andExpect(status().isForbidden());
		perform(post("/api/addresses/create"), accessToken(newUser("ADMIN").getId()),
				address(customer.getId(), "\"postalCode\": \"500081\""))
				.andExpect(status().isCreated());

		perform(delete("/api/addresses/delete/" + secondId), customerToken, null).andExpect(status().isNoContent());
		perform(get("/api/addresses/" + secondId), customerToken, null).andExpect(status().isNotFound());
	}

	@Test
	void staffProfilesNeedStaffManageForEmployeeDetails() throws Exception {
		NexHomeUser subAdmin = newUser("SUB_ADMIN");
		String subAdminToken = accessToken(subAdmin.getId());
		String admin = accessToken(newUser("ADMIN").getId());
		NexHomeUser superUser = newUser("SUPER_USER");
		String superUserToken = accessToken(superUser.getId());

		// Staff create their own profile without employee details
		JsonNode own = json(perform(post("/api/profiles/admins/create"), subAdminToken,
				staffProfile(subAdmin.getId(), ""))
				.andExpect(status().isCreated()));
		assertThat(own.get("publicId").asString()).matches("NHADMI\\d{4,}");
		long ownId = own.get("adminProfileId").asLong();
		perform(put("/api/profiles/admins/update"), subAdminToken,
				"{\"adminProfileId\": %d, \"city\": \"Vijayawada\"}".formatted(ownId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.city").value("Vijayawada"));
		perform(put("/api/profiles/admins/update"), subAdminToken,
				"{\"adminProfileId\": %d, \"jobTitle\": \"Head\"}".formatted(ownId))
				.andExpect(status().isForbidden());

		// Admins (ADMIN_MANAGE / SUB_ADMIN_MANAGE write) set employee details; codes are unique
		NexHomeUser otherSubAdmin = newUser("SUB_ADMIN");
		String code = "emp-" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
		long otherId = json(perform(post("/api/profiles/admins/create"), admin,
				staffProfile(otherSubAdmin.getId(), ", \"employeeCode\": \"" + code + "\""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.employeeCode").value(code.toUpperCase()))).get("adminProfileId").asLong();
		// Sub-admins read other staff profiles (ADMIN_MANAGE / SUB_ADMIN_MANAGE read) but do not change them
		perform(get("/api/profiles/admins/" + otherId), subAdminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.employeeCode").value(code.toUpperCase()));
		perform(get("/api/profiles/admins?search=" + code), subAdminToken, null).andExpect(status().isOk());
		perform(put("/api/profiles/admins/update"), subAdminToken,
				"{\"adminProfileId\": %d, \"city\": \"Guntur\"}".formatted(otherId))
				.andExpect(status().isForbidden());
		perform(put("/api/profiles/admins/update"), admin,
				"{\"adminProfileId\": %d, \"employeeCode\": \"%s\"}".formatted(ownId, code.toUpperCase()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Another staff profile has this employee code."));

		// A super user's profile is only changed by a super user
		long superProfileId = json(perform(post("/api/profiles/admins/create"), superUserToken,
				staffProfile(superUser.getId(), ", \"employeeCode\": \"\""))
				.andExpect(status().isCreated())).get("adminProfileId").asLong();
		perform(put("/api/profiles/admins/update"), admin,
				"{\"adminProfileId\": %d, \"city\": \"Guntur\"}".formatted(superProfileId))
				.andExpect(status().isForbidden());

		perform(put("/api/profiles/admins/update"), subAdminToken,
				"{\"adminProfileId\": %d, \"city\": \"Guntur\"}".formatted(superProfileId))
				.andExpect(status().isForbidden());
		perform(get("/api/profiles/admins/me"), subAdminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.adminProfileId").value(ownId));
		perform(get("/api/profiles/admins"), admin, null).andExpect(status().isOk());
		perform(get("/api/profiles/admins"), subAdminToken, null).andExpect(status().isOk());
		perform(get("/api/profiles/admins"), accessToken(newUser("CUSTOMER").getId()), null)
				.andExpect(status().isForbidden());

		NexHomeUser customer = newUser("CUSTOMER");
		perform(post("/api/profiles/admins/create"), accessToken(customer.getId()), staffProfile(customer.getId(), ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Staff profiles are only for super users, admins and sub-admins."));
	}

	@Test
	void partnerIsOnboardedThroughReview() throws Exception {
		NexHomeUser partner = newUser("PARTNER");
		String partnerToken = accessToken(partner.getId());
		NexHomeUser adminUser = newUser("ADMIN");
		String admin = accessToken(adminUser.getId());
		String subAdmin = accessToken(newUser("SUB_ADMIN").getId());

		JsonNode draft = json(perform(post("/api/partners/profiles/create"), partnerToken, """
				{"userId": %d, "firstName": "Suresh", "lastName": "Babu", "aadhaarNumber": "234567890123",
				 "emergencyContactName": "Lakshmi", "emergencyContactRelationship": "SPOUSE",
				 "emergencyContactNumber": "9000000001"}""".formatted(partner.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.onboardingStatus").value("DRAFT"))
				.andExpect(jsonPath("$.approvalStatus").value("PENDING"))
				.andExpect(jsonPath("$.availabilityStatus").value("OFFLINE"))
				.andExpect(jsonPath("$.aadhaarNumber").value("XXXXXXXX0123"))
				.andExpect(jsonPath("$.registeredBy").value(partner.getId()))
				.andExpect(jsonPath("$.registrationDate").value(LocalDate.now().toString())));
		long profileId = draft.get("partnerProfileId").asLong();
		String publicId = draft.get("publicId").asString();
		assertThat(publicId).matches("NHPART\\d{4,}");

		// The console's partner list shows the profile, finds it by name or public id, and filters by onboarding
		// status, including partners who have not started (NOT_STARTED); counts by onboarding
		NexHomeUser notStarted = newUser("PARTNER");
		perform(get("/api/users/partners?search=suresh babu"), subAdmin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.userId == %d)].publicId".formatted(partner.getId())).value(publicId))
				.andExpect(jsonPath("$.content[?(@.userId == %d)].onboardingStatus".formatted(partner.getId())).value("DRAFT"));
		perform(get("/api/users/partners?onboardingStatus=DRAFT&search=" + publicId.toLowerCase()), subAdmin, null)
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].availabilityStatus").value("OFFLINE"))
				.andExpect(jsonPath("$.content[0].roles[0].roleCode").value("PARTNER"));
		perform(get("/api/users/partners?onboardingStatus=NOT_STARTED&search=" + notStarted.getMobileNumber()), subAdmin,
				null)
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].publicId").doesNotExist());
		perform(get("/api/users/partners?onboardingStatus=APPROVED&search=" + publicId), subAdmin, null)
				.andExpect(jsonPath("$.totalElements").value(0));
		perform(get("/api/users/partners?onboardingStatus=SOMETHING"), subAdmin, null).andExpect(status().isBadRequest());
		perform(get("/api/users/partners/stats"), subAdmin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.notSubmitted").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
		perform(get("/api/users/partners/stats"), partnerToken, null).andExpect(status().isForbidden());

		// Only the ciphertext is stored
		String stored = (String) entityManager.createNativeQuery(
				"select aadhaar_number_encrypted from nexhome_partner_profiles where id = :id")
				.setParameter("id", profileId).getSingleResult();
		assertThat(stored).startsWith("v1:").doesNotContain("234567890123");

		perform(put("/api/partners/profiles/submit"), partnerToken, "{\"partnerProfileId\": %d}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Complete these fields first: isAdult, "
						+ "preferredContactLanguage, addressLine1")));
		perform(put("/api/partners/profiles/update"), partnerToken,
				"{\"partnerProfileId\": %d, \"registrationDate\": \"2026-01-01\"}".formatted(profileId))
				.andExpect(status().isForbidden());
		perform(put("/api/partners/profiles/update"), partnerToken,
				"{\"partnerProfileId\": %d, \"alternateContactNumber\": \"%s\"}".formatted(profileId,
						partner.getMobileNumber()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Alternate contact number must differ from the partner's mobile number."));
		perform(put("/api/partners/profiles/update"), partnerToken,
				"{\"partnerProfileId\": %d, \"weeklyAvailability\": {\"FUNDAY\": [\"MORNING\"]}}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Weekly availability days must be MONDAY to SUNDAY."));

		// Lists are deduplicated, the grid is normalized; the weekend answer must agree with it
		perform(put("/api/partners/profiles/update"), partnerToken, completeProfile(profileId, newCategory().getId(), "SUNDAY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.serviceAreas.length()").value(2))
				.andExpect(jsonPath("$.serviceAreas[1]").value("Kondapur"))
				.andExpect(jsonPath("$.weeklyAvailability.MONDAY[0]").value("MORNING"))
				.andExpect(jsonPath("$.weeklyAvailability.MONDAY[1]").value("EVENING"))
				.andExpect(jsonPath("$.weeklyAvailability.SATURDAY[0]").value("MORNING"));
		perform(put("/api/partners/profiles/submit"), partnerToken, "{\"partnerProfileId\": %d}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Weekly availability does not match weekend "
						+ "availability SUNDAY")));
		perform(put("/api/partners/profiles/update"), partnerToken,
				"{\"partnerProfileId\": %d, \"weekendAvailability\": \"SATURDAY\"}".formatted(profileId))
				.andExpect(status().isOk());
		perform(put("/api/partners/profiles/submit"), partnerToken, "{\"partnerProfileId\": %d}".formatted(profileId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onboardingStatus").value("UNDER_REVIEW"))
				.andExpect(jsonPath("$.approvalStatus").value("IN_REVIEW"));

		// Under review the partner can no longer edit
		perform(put("/api/partners/profiles/update"), partnerToken,
				"{\"partnerProfileId\": %d, \"city\": \"Pune\"}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("The profile is UNDER_REVIEW")));

		// Staff always see the emergency contact (V21)
		perform(get("/api/partners/profiles/" + profileId), admin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emergencyContactName").value("Lakshmi"));
		perform(get("/api/partners/profiles/me"), partnerToken, null)
				.andExpect(jsonPath("$.emergencyContactName").value("Lakshmi"));

		// Reviews need PARTNER_ONBOARDING_MANAGE write
		perform(put("/api/partners/profiles/review"), subAdmin,
				"{\"partnerProfileId\": %d, \"onboardingStatus\": \"APPROVED\"}".formatted(profileId))
				.andExpect(status().isForbidden());
		perform(put("/api/partners/profiles/review"), admin,
				"{\"partnerProfileId\": %d, \"onboardingStatus\": \"REJECTED\"}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Rejection reason is required to move a profile to REJECTED."));
		perform(put("/api/partners/profiles/availability/update"), partnerToken,
				"{\"partnerProfileId\": %d, \"availabilityStatus\": \"ONLINE\"}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Only an APPROVED partner can change availability."));
		// Approval needs the three verification checks
		perform(put("/api/partners/profiles/review"), admin,
				"{\"partnerProfileId\": %d, \"onboardingStatus\": \"APPROVED\"}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Verify the account, documents and bank account before approving the partner."));
		// The documents and bank account checks come from those records (V24)
		long documentId = json(perform(post("/api/partners/documents/add"), admin, """
				{"partnerProfileId": %d, "documentType": "GOVERNMENT_ID", "idType": "PAN", "documentNumber": "ABCDE1234F"}"""
				.formatted(profileId))
				.andExpect(status().isCreated())).get("partnerDocumentId").asLong();
		long bankAccountId = json(perform(post("/api/partners/bank-accounts/add"), admin, """
				{"partnerProfileId": %d, "accountHolderName": "Suresh Babu", "accountNumber": "123456784321",
				 "ifscCode": "SBIN0001234", "bankName": "SBI"}""".formatted(profileId))
				.andExpect(status().isCreated())).get("partnerBankAccountId").asLong();
		perform(put("/api/partners/documents/verify"), admin,
				"{\"partnerDocumentId\": %d, \"verificationStatus\": \"VERIFIED\"}".formatted(documentId))
				.andExpect(status().isOk());
		perform(put("/api/partners/bank-accounts/verify"), admin,
				"{\"partnerBankAccountId\": %d, \"verificationStatus\": \"VERIFIED\"}".formatted(bankAccountId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.updatedBy").value(adminUser.getId()));
		perform(put("/api/partners/profiles/verifications/update"), admin,
				"{\"partnerProfileId\": %d, \"accountVerified\": true}".formatted(profileId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountVerifiedBy").value(adminUser.getId()))
				.andExpect(jsonPath("$.documentsVerified").value(true))
				.andExpect(jsonPath("$.bankAccVerified").value(true));
		perform(put("/api/partners/profiles/review"), admin,
				"{\"partnerProfileId\": %d, \"onboardingStatus\": \"APPROVED\"}".formatted(profileId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onboardingStatus").value("APPROVED"))
				.andExpect(jsonPath("$.approvalStatus").value("VERIFIED"))
				.andExpect(jsonPath("$.approvedBy").value(adminUser.getId()))
				.andExpect(jsonPath("$.approvedAt").isNotEmpty());
		perform(put("/api/partners/profiles/verifications/update"), admin,
				"{\"partnerProfileId\": %d, \"accountVerified\": false}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The account of an approved partner cannot be unverified."));

		perform(put("/api/partners/profiles/availability/update"), partnerToken,
				"{\"partnerProfileId\": %d, \"availabilityStatus\": \"ONLINE\"}".formatted(profileId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availabilityStatus").value("ONLINE"));
		perform(put("/api/partners/profiles/review"), admin, """
				{"partnerProfileId": %d, "onboardingStatus": "CHANGES_REQUESTED", "rejectionReason": "Upload a new ID"}"""
				.formatted(profileId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availabilityStatus").value("OFFLINE"))
				.andExpect(jsonPath("$.approvalStatus").value("PENDING"))
				.andExpect(jsonPath("$.rejectionReason").value("Upload a new ID"));
		// Back with the partner: staff cannot approve it until it is submitted again
		perform(put("/api/partners/profiles/review"), admin,
				"{\"partnerProfileId\": %d, \"onboardingStatus\": \"APPROVED\"}".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("A profile that is CHANGES_REQUESTED cannot be moved to APPROVED."));

		perform(get("/api/partners/profiles?onboardingStatus=CHANGES_REQUESTED&city=HYDERABAD&search=" + publicId),
				subAdmin,
				null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
		perform(get("/api/partners/profiles?onboardingStatus=NOPE"), subAdmin, null)
				.andExpect(status().isBadRequest());
		perform(get("/api/partners/profiles"), partnerToken, null).andExpect(status().isForbidden());
	}

	@Test
	void nobodyReviewsTheirOwnPartnerProfile() throws Exception {
		String admin = accessToken(newUser("ADMIN").getId());
		NexHomeUser adminPartner = newUser("ADMIN");
		userRoleRepository.saveAndFlush(userMapper.toUserRoleEntity(adminPartner,
				roleRepository.findByCode("PARTNER").orElseThrow(), 1L));

		// Staff may register a partner; registeredBy records them
		long profileId = json(perform(post("/api/partners/profiles/create"), admin,
				"{\"userId\": %d, \"firstName\": \"Anil\", \"lastName\": \"Rao\"}".formatted(adminPartner.getId()))
				.andExpect(status().isCreated())).get("partnerProfileId").asLong();
		perform(put("/api/partners/profiles/review"), accessToken(adminPartner.getId()),
				"{\"partnerProfileId\": %d, \"onboardingStatus\": \"APPROVED\"}".formatted(profileId))
				.andExpect(status().isForbidden());
	}

	@Test
	void partnerDocumentsAndBankAccounts() throws Exception {
		NexHomeUser partner = newUser("PARTNER");
		String partnerToken = accessToken(partner.getId());
		String otherPartner = accessToken(newUser("PARTNER").getId());
		NexHomeUser adminUser = newUser("ADMIN");
		String admin = accessToken(adminUser.getId());
		long profileId = json(perform(post("/api/partners/profiles/create"), partnerToken,
				"{\"userId\": %d, \"firstName\": \"Suresh\", \"lastName\": \"Babu\"}".formatted(partner.getId())))
				.get("partnerProfileId").asLong();

		long documentId = json(perform(post("/api/partners/documents/add"), partnerToken, """
				{"partnerProfileId": %d, "documentType": "GOVERNMENT_ID", "idType": "PAN", "documentNumber": "abcde1234f",
				 "fileStorageKey": "partners/%d/pan.pdf", "expiryDate": "%s"}"""
				.formatted(profileId, profileId, LocalDate.now().plusYears(5)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.documentNumber").value("XXXXXX234F"))
				.andExpect(jsonPath("$.verificationStatus").value("PENDING")))
				.get("partnerDocumentId").asLong();
		perform(post("/api/partners/documents/add"), partnerToken, """
				{"partnerProfileId": %d, "documentType": "CERTIFICATION", "fileStorageKey": "k",
				 "expiryDate": "2020-01-01"}""".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Expiry date cannot be in the past"));
		perform(get("/api/partners/profiles/documents/" + profileId), otherPartner, null)
				.andExpect(status().isForbidden());

		perform(put("/api/partners/documents/verify"), partnerToken,
				"{\"partnerDocumentId\": %d, \"verificationStatus\": \"VERIFIED\"}".formatted(documentId))
				.andExpect(status().isForbidden());
		perform(put("/api/partners/documents/verify"), admin,
				"{\"partnerDocumentId\": %d, \"verificationStatus\": \"PENDING\"}".formatted(documentId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Verification status must be one of: IN_REVIEW, VERIFIED, "
						+ "REJECTED, EXPIRED."));
		perform(put("/api/partners/documents/verify"), admin,
				"{\"partnerDocumentId\": %d, \"verificationStatus\": \"VERIFIED\"}".formatted(documentId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verifiedBy").value(adminUser.getId()));
		perform(put("/api/partners/documents/update"), partnerToken,
				"{\"partnerDocumentId\": %d, \"fileStorageKey\": \"new\"}".formatted(documentId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("A verified document cannot be changed; add a new document instead."));
		perform(delete("/api/partners/documents/delete/" + documentId), partnerToken, null)
				.andExpect(status().isBadRequest());

		JsonNode first = json(perform(post("/api/partners/bank-accounts/add"), partnerToken, """
				{"partnerProfileId": %d, "accountHolderName": "Suresh  Babu", "accountNumber": "123456784321",
				 "ifscCode": "sbin0001234", "bankName": "SBI"}""".formatted(profileId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.isPrimary").value(true))
				.andExpect(jsonPath("$.accountNumberLast4").value("4321"))
				.andExpect(jsonPath("$.ifscCode").value("SBIN0001234"))
				.andExpect(jsonPath("$.accountHolderName").value("Suresh Babu"))
				.andExpect(jsonPath("$.accountNumber").doesNotExist()));
		long firstId = first.get("partnerBankAccountId").asLong();
		String storedNumber = (String) entityManager.createNativeQuery(
				"select account_number_encrypted from nexhome_partner_bank_accounts where id = :id")
				.setParameter("id", firstId).getSingleResult();
		assertThat(storedNumber).doesNotContain("123456784321");

		perform(post("/api/partners/bank-accounts/add"), partnerToken, """
				{"partnerProfileId": %d, "accountHolderName": "Suresh", "accountNumber": "123456784321",
				 "ifscCode": "SBIN0001234"}""".formatted(profileId))
				.andExpect(status().isConflict());
		perform(post("/api/partners/bank-accounts/add"), partnerToken, """
				{"partnerProfileId": %d, "accountHolderName": "Suresh", "accountNumber": "99887766",
				 "ifscCode": "HDFC0000001"}""".formatted(profileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Account number must be 9 to 18 digits"));
		long secondId = json(perform(post("/api/partners/bank-accounts/add"), partnerToken, """
				{"partnerProfileId": %d, "accountHolderName": "Suresh", "accountNumber": "998877665",
				 "ifscCode": "HDFC0000001", "isPrimary": true}""".formatted(profileId))
				.andExpect(status().isCreated())).get("partnerBankAccountId").asLong();
		perform(get("/api/partners/profiles/bank-accounts/" + profileId), partnerToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].partnerBankAccountId").value(secondId))
				.andExpect(jsonPath("$[1].isPrimary").value(false));

		perform(put("/api/partners/bank-accounts/update"), partnerToken,
				"{\"partnerBankAccountId\": %d, \"accountNumber\": \"111111111\"}".formatted(firstId))
				.andExpect(status().isBadRequest());
		perform(put("/api/partners/bank-accounts/verify"), admin,
				"{\"partnerBankAccountId\": %d, \"verificationStatus\": \"VERIFIED\"}".formatted(firstId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
		// A new holder name needs verifying again
		perform(put("/api/partners/bank-accounts/update"), partnerToken,
				"{\"partnerBankAccountId\": %d, \"accountHolderName\": \"S Babu\"}".formatted(firstId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verificationStatus").value("PENDING"));

		perform(delete("/api/partners/bank-accounts/delete/" + firstId), otherPartner, null)
				.andExpect(status().isForbidden());
		perform(delete("/api/partners/bank-accounts/delete/" + firstId), partnerToken, null)
				.andExpect(status().isNoContent());
	}

	private static String address(Long userId, String extra) {
		return """
				{"userId": %d, "contactName": "Ravi", "mobileNumber": "9876543210", "addressType": "HOME",
				 "label": "Home", "addressLine1": "Plot 12, Road 3", "city": "Hyderabad", "state": "Telangana", %s}"""
				.formatted(userId, extra);
	}

	private static String staffProfile(Long userId, String extra) {
		return """
				{"userId": %d, "firstName": "Priya", "lastName": "Sharma", "addressLine1": "Flat 4, Banjara Hills",
				 "city": "Hyderabad", "state": "Telangana", "postalCode": "500034"%s}""".formatted(userId, extra);
	}

	private static String completeProfile(long profileId, long categoryId, String weekend) {
		return """
				{"partnerProfileId": %d, "mainServiceCategoryId": %d, "isAdult": true, "preferredContactLanguage": "te",
				 "addressLine1": "H.No 1-2-3", "locality": "Madhapur", "city": "Hyderabad", "state": "Telangana",
				 "postalCode": "500081", "skillCategory": "SKILLED", "yearsExperience": 6,
				 "preferredArrangement": "FLEXIBLE_JOBS", "paymentBasis": "PER_JOB", "expectedEarningsInr": 800,
				 "maxTravelKm": 12.5, "serviceAreas": ["Madhapur", " madhapur ", "Kondapur"],
				 "weeklyAvailability": {"monday": ["evening", "MORNING", "EVENING"], "saturday": ["MORNING"]},
				 "weekendAvailability": "%s"}""".formatted(profileId, categoryId, weekend);
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

	/** An active service category, created inside the test's transaction. */
	private NexHomeServiceCategory newCategory() {
		NexHomeServiceCategory category = new NexHomeServiceCategory();
		category.setName("Test Category");
		category.setSlug("test-category-" + UUID.randomUUID());
		return categoryRepository.saveAndFlush(category);
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
