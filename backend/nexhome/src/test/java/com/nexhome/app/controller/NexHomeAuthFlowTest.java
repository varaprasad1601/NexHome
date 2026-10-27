package com.nexhome.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.nexhome.app.model.NexHomeAuthOtpRequest;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAuthOtpRequestRepository;
import com.nexhome.app.repository.NexHomeAuthRefreshTokenRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End to end through HTTP and the security filters, against the dev database (rolled back): sign-up, OTP login,
 * tokens, refresh rotation and reuse detection, logout, and role-function authorization with JSON error bodies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class NexHomeAuthFlowTest {

	private static final String KNOWN_OTP = "123456";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JwtEncoder jwtEncoder;
	@Autowired
	private NexHomeSecurityProperties securityProperties;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private NexHomeAuthOtpRequestRepository otpRequestRepository;
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
	void protectedEndpointsNeedAValidAccessToken() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.statusCode").value(401))
				.andExpect(jsonPath("$.message").value("Authentication is required."));

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("The access token is invalid or has expired."));

		// Public: dropdown options
		mockMvc.perform(get("/api/lookups/values/ACCOUNT_STATUS")).andExpect(status().isOk());
	}

	@Test
	void signUpLoginRefreshAndLogout() throws Exception {
		String mobile = randomMobile();
		perform(post("/api/auth/register"), null,
				"{\"countryCode\":\"+91\",\"mobileNumber\":\"" + mobile + "\",\"roleCode\":\"CUSTOMER\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.purpose").value("REGISTRATION"))
				.andExpect(jsonPath("$.tokens").doesNotExist());

		JsonNode verified = json(perform(post("/api/auth/otp/verify"), null, verifyBody(mobile, "REGISTRATION"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requestStatus").value("VERIFIED"))
				.andExpect(jsonPath("$.user.roles[0].roleCode").value("CUSTOMER")));
		String accessToken = verified.get("tokens").get("accessToken").asString();
		String refreshToken = verified.get("tokens").get("refreshToken").asString();

		perform(get("/api/users/me"), accessToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mobileNumber").value(mobile))
				.andExpect(jsonPath("$.accountVerifiedDate").isNotEmpty());

		// A customer has no role functions: admin endpoints are forbidden
		perform(post("/api/roles/create"), accessToken, "{\"code\":\"HACKER\",\"name\":\"Hacker\"}")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("You do not have permission to perform this action."));

		JsonNode refreshed = json(perform(post("/api/auth/token/refresh"), null, refreshBody(refreshToken))
				.andExpect(status().isOk()));
		String newRefreshToken = refreshed.get("refreshToken").asString();
		assertThat(newRefreshToken).isNotEqualTo(refreshToken);
		// Refreshing continues the session: the earlier access token still works, and so does the new one
		perform(get("/api/users/me"), accessToken, null).andExpect(status().isOk());
		perform(get("/api/users/me"), refreshed.get("accessToken").asString(), null).andExpect(status().isOk());

		// Reusing the rotated token is rejected and ends every session, including the new one and its access tokens
		perform(post("/api/auth/token/refresh"), null, refreshBody(refreshToken))
				.andExpect(status().isUnauthorized());
		perform(post("/api/auth/token/refresh"), null, refreshBody(newRefreshToken))
				.andExpect(status().isUnauthorized());
		expectSessionEnded(accessToken);

		// Log in again with a LOGIN OTP, then log out
		perform(post("/api/auth/otp/request"), null,
				"{\"countryCode\":\"+91\",\"mobileNumber\":\"" + mobile + "\",\"purpose\":\"LOGIN\"}")
				.andExpect(status().isCreated());
		JsonNode login = json(perform(post("/api/auth/otp/verify"), null, verifyBody(mobile, "LOGIN"))
				.andExpect(status().isOk())).get("tokens");
		String loginAccessToken = login.get("accessToken").asString();
		String loginRefreshToken = login.get("refreshToken").asString();
		perform(get("/api/users/me"), loginAccessToken, null).andExpect(status().isOk());

		perform(post("/api/auth/logout"), null, refreshBody(loginRefreshToken)).andExpect(status().isNoContent());
		perform(post("/api/auth/token/refresh"), null, refreshBody(loginRefreshToken))
				.andExpect(status().isUnauthorized());
		// The access token of the logged-out session stops working at once, not when it expires
		expectSessionEnded(loginAccessToken);
	}

	@Test
	void adminOtpIsSentOnlyToStaff() throws Exception {
		String unknown = randomMobile();
		NexHomeUser customer = newUser("CUSTOMER");
		NexHomeUser subAdmin = newUser("SUB_ADMIN");

		for (String mobile : List.of(unknown, customer.getMobileNumber())) {
			perform(post("/api/auth/admin/otp/request"), null,
					"{\"countryCode\":\"+91\",\"mobileNumber\":\"" + mobile + "\"}")
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.message").value(
							"Invalid user. This mobile number does not have admin access."));
		}

		// Public endpoint; the OTP is verified like any LOGIN OTP
		perform(post("/api/auth/admin/otp/request"), null,
				"{\"countryCode\":\"+91\",\"mobileNumber\":\"" + subAdmin.getMobileNumber() + "\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.purpose").value("LOGIN"));
		perform(post("/api/auth/otp/verify"), null, verifyBody(subAdmin.getMobileNumber(), "LOGIN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.roles[0].roleCode").value("SUB_ADMIN"));
	}

	@Test
	void staffAreListedWithTheirProfileNames() throws Exception {
		NexHomeUser superUser = newUser("SUPER_USER");
		NexHomeUser admin = newUser("ADMIN");
		String superUserToken = accessToken(superUser.getId());
		String adminToken = accessToken(admin.getId());
		String subAdminToken = accessToken(newUser("SUB_ADMIN").getId());
		String mobile = randomMobile();

		// Add Admin uses Create user (ADMIN is role 2): the account, its role and its empty staff profile
		JsonNode created = json(perform(post("/api/users/create"), adminToken,
				"{\"countryCode\":\"+91\",\"mobileNumber\":\"" + mobile + "\",\"email\":\"admin." + mobile
						+ "@nexhome.com\",\"roleId\":2}")
				.andExpect(status().isCreated()));
		JsonNode listed = json(perform(get("/api/users/staff?search=" + mobile), adminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].userId").value(created.get("userId").asLong()))
				.andExpect(jsonPath("$.content[0].roleName").value("Admin"))
				.andExpect(jsonPath("$.content[0].publicId").value(startsWith("NHADMI")))
				// Names come later, from My Profile
				.andExpect(jsonPath("$.content[0].firstName").doesNotExist())).get("content").get(0);
		// Only one staff profile per account
		perform(post("/api/profiles/admins/create"), adminToken, "{\"userId\":" + created.get("userId").asLong()
				+ ",\"firstName\":\"Rahul\",\"lastName\":\"Mehta\",\"addressLine1\":\"1 Main Road\","
				+ "\"city\":\"Nuzvid\",\"state\":\"Andhra Pradesh\",\"postalCode\":\"521201\"}")
				.andExpect(status().isConflict());

		// Once the names are filled in, they are listed and searchable ("first last")
		String lastName = "Mehta" + randomLetters(6);
		perform(put("/api/profiles/admins/update"), adminToken, "{\"adminProfileId\":"
				+ listed.get("adminProfileId").asLong() + ",\"firstName\":\"Rahul\",\"lastName\":\"" + lastName + "\"}")
				.andExpect(status().isOk());
		perform(get("/api/users/staff?search=rahul " + lastName.toLowerCase()), adminToken, null)
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].mobileNumber").value(mobile))
				.andExpect(jsonPath("$.content[0].firstName").value("Rahul"));
		perform(get("/api/users/staff/stats?roleCode=ADMIN"), adminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(2)));
		perform(get("/api/users/staff?roleCode=SUB_ADMIN&search=" + mobile), adminToken, null)
				.andExpect(jsonPath("$.totalElements").value(0));
		// Sub-admins see the staff lists (ADMIN_MANAGE / SUB_ADMIN_MANAGE read) but cannot add staff or change their status
		perform(get("/api/users/staff?search=" + mobile), subAdminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
		perform(get("/api/users/staff/stats?roleCode=ADMIN"), subAdminToken, null).andExpect(status().isOk());
		perform(post("/api/users/create"), subAdminToken,
				"{\"countryCode\":\"+91\",\"mobileNumber\":\"" + randomMobile() + "\",\"roleId\":3}")
				.andExpect(status().isForbidden());
		perform(put("/api/users/status/update"), subAdminToken, "{\"userId\":" + created.get("userId").asLong()
				+ ",\"accountStatus\":\"SUSPENDED\"}")
				.andExpect(status().isForbidden());
		perform(get("/api/users/staff?roleCode=CUSTOMER"), adminToken, null).andExpect(status().isBadRequest());

		// Super users are listed and counted for super users only
		perform(get("/api/users/staff?search=" + superUser.getMobileNumber()), adminToken, null)
				.andExpect(jsonPath("$.totalElements").value(0));
		perform(get("/api/users/staff?search=" + superUser.getMobileNumber()), superUserToken, null)
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].roleCode").value("SUPER_USER"));
		long adminTotal = json(perform(get("/api/users/staff/stats"), adminToken, null)
				.andExpect(status().isOk())).get("total").asLong();
		JsonNode superStats = json(perform(get("/api/users/staff/stats"), superUserToken, null));
		assertThat(superStats.get("total").asLong()).isGreaterThan(adminTotal);
		assertThat(superStats.get("active").asLong() + superStats.get("inactive").asLong()
				+ superStats.get("suspended").asLong() + superStats.get("blocked").asLong())
				.isEqualTo(superStats.get("total").asLong());
	}

	@Test
	void logoutEverywhereEndsEverySession() throws Exception {
		NexHomeUser user = newUser("CUSTOMER");
		String first = accessToken(user.getId());
		String second = accessToken(user.getId());

		perform(post("/api/auth/logout/all"), first, null).andExpect(status().isNoContent());

		expectSessionEnded(first);
		expectSessionEnded(second);
	}

	@Test
	void blockingAnAccountEndsItsSessions() throws Exception {
		String superUser = accessToken(newUser("SUPER_USER").getId());
		NexHomeUser customer = newUser("CUSTOMER");
		String customerToken = accessToken(customer.getId());
		perform(get("/api/users/me"), customerToken, null).andExpect(status().isOk());

		perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/status/update"),
				superUser, "{\"userId\":" + customer.getId() + ",\"accountStatus\":\"BLOCKED\"}")
				.andExpect(status().isOk());

		expectSessionEnded(customerToken);
	}

	@Test
	void roleListsFollowTheRequestedAccess() throws Exception {
		NexHomeUser customer = newUser("CUSTOMER");
		NexHomeUser admin = newUser("ADMIN");
		NexHomeUser subAdmin = newUser("SUB_ADMIN");
		NexHomeUser superUser = newUser("SUPER_USER");
		String superUserToken = accessToken(superUser.getId());
		String adminToken = accessToken(admin.getId());
		String subAdminToken = accessToken(subAdmin.getId());
		String customerToken = accessToken(customer.getId());

		// Customers and partners: SUPER_USER, ADMIN and SUB_ADMIN
		for (String token : List.of(superUserToken, adminToken, subAdminToken)) {
			perform(get("/api/users/customers?search=" + customer.getMobileNumber()), token, null)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(1))
					.andExpect(jsonPath("$.content[0].userId").value(customer.getId()))
					.andExpect(jsonPath("$.content[0].roles[0].roleCode").value("CUSTOMER"));
			perform(get("/api/users/partners"), token, null).andExpect(status().isOk());
		}
		// Admins and sub-admins: SUPER_USER, ADMIN and SUB_ADMIN (ADMIN_MANAGE / SUB_ADMIN_MANAGE read)
		for (String token : List.of(superUserToken, adminToken, subAdminToken)) {
			perform(get("/api/users/admins?search=" + admin.getMobileNumber()), token, null)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[0].userId").value(admin.getId()));
			perform(get("/api/users/sub-admins?search=" + subAdmin.getMobileNumber()), token, null)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[0].userId").value(subAdmin.getId()));
		}

		// Super users: SUPER_USER only, never through role functions (an admin is refused)
		perform(get("/api/users/super-users?search=" + superUser.getMobileNumber()), superUserToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].userId").value(superUser.getId()))
				.andExpect(jsonPath("$.content[0].roles[0].roleCode").value("SUPER_USER"));
		for (String token : List.of(adminToken, subAdminToken, customerToken)) {
			perform(get("/api/users/super-users"), token, null).andExpect(status().isForbidden());
		}
		// Customers see none of the lists
		perform(get("/api/users/customers"), customerToken, null).andExpect(status().isForbidden());
		perform(get("/api/users/stats"), customerToken, null).andExpect(status().isForbidden());

		// Dashboard stats count current holders; a new customer is added this month
		JsonNode before = json(perform(get("/api/users/stats"), subAdminToken, null).andExpect(status().isOk()))
				.get("customers");
		newUser("CUSTOMER");
		perform(get("/api/users/stats"), subAdminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customers.roleCode").value("CUSTOMER"))
				.andExpect(jsonPath("$.customers.total").value(before.get("total").asLong() + 1))
				.andExpect(jsonPath("$.customers.addedThisMonth").value(before.get("addedThisMonth").asLong() + 1))
				.andExpect(jsonPath("$.customers.months.length()").value(10))
				.andExpect(jsonPath("$.customers.months[9].total").value(before.get("total").asLong() + 1))
				.andExpect(jsonPath("$.partners.roleCode").value("PARTNER"));
		perform(get("/api/users/admins"), customerToken, null).andExpect(status().isForbidden());

		// Filters, paging and bad parameters
		perform(get("/api/users/customers?accountStatus=BLOCKED&search=" + customer.getMobileNumber()),
				adminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
		perform(get("/api/users/customers"), adminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10));
		perform(get("/api/users/customers?page=0&size=1"), adminToken, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.content.length()").value(1));
		perform(get("/api/users/customers?size=500"), adminToken, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Size must be between 1 and 100."));
		perform(get("/api/users/customers?page=abc"), adminToken, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Parameter page has an invalid value."));
	}

	/**
	 * The PostgreSQL driver switches to server-side prepared statements after a few executions of the same SQL; the
	 * unfiltered list must keep working after that (regression: 500 from untyped null filter parameters).
	 */
	@Test
	void unfilteredListsKeepWorkingWhenCalledRepeatedly() throws Exception {
		String superUser = accessToken(newUser("SUPER_USER").getId());
		for (int i = 0; i < 8; i++) {
			perform(get("/api/users/customers"), superUser, null).andExpect(status().isOk());
			perform(get("/api/users/admins?page=0&size=5"), superUser, null).andExpect(status().isOk());
		}
	}

	private void expectSessionEnded(String accessToken) throws Exception {
		perform(get("/api/users/me"), accessToken, null)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("The session has ended. Log in again."));
	}

	@Test
	void roleFunctionsDecideAdminAccess() throws Exception {
		Short adminRoleId = roleRepository.findByCode("ADMIN").orElseThrow().getId();
		Short superUserRoleId = roleRepository.findByCode("SUPER_USER").orElseThrow().getId();
		// The test's own super user, so it does not depend on which accounts the database holds
		String superUser = accessToken(newUser("SUPER_USER").getId());

		// SUPER_USER may do anything, e.g. create a staff account with the ADMIN role
		long adminId = json(perform(post("/api/users/create"), superUser,
				"{\"countryCode\":\"+91\",\"mobileNumber\":\"" + randomMobile() + "\",\"roleId\":" + adminRoleId + "}")
				.andExpect(status().isCreated())).get("userId").asLong();
		String admin = accessToken(adminId);

		// Lookups are changed by super users only (V11): ADMIN reads them. Roles need ROLE_MANAGE write, which no
		// seeded role but SUPER_USER is meant to have (a sub-admin is used, since the dev DB's grants may be edited)
		perform(get("/api/lookups/types"), admin, null).andExpect(status().isOk());
		perform(post("/api/lookups/types/create"), admin, "{\"typeCode\":\"TEST_FLOW\",\"typeName\":\"Test Flow\"}")
				.andExpect(status().isForbidden());
		perform(post("/api/lookups/types/create"), superUser, "{\"typeCode\":\"TEST_FLOW\",\"typeName\":\"Test Flow\"}")
				.andExpect(status().isCreated());
		perform(post("/api/roles/create"), accessToken(newUser("SUB_ADMIN").getId()),
				"{\"code\":\"TEST_FLOW\",\"name\":\"Test Flow\"}")
				.andExpect(status().isForbidden());
		long testRoleId = json(perform(post("/api/roles/create"), superUser,
				"{\"code\":\"TEST_FLOW\",\"name\":\"Test Flow\"}")
				.andExpect(status().isCreated())).get("roleId").asLong();
		// The System Configuration lists: ROLE_MANAGE / FUNCTION_MANAGE read (ADMIN yes, SUB_ADMIN no)
		perform(get("/api/roles"), admin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].code").value("SUPER_USER"))
				.andExpect(jsonPath("$[?(@.code == 'TEST_FLOW')].activeFlag").value(true));
		perform(get("/api/functions"), admin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.functionCode == 'ROLE_MANAGE')].functionModule").value("SYSTEM_CONFIGURATION"));
		String subAdmin = accessToken(newUser("SUB_ADMIN").getId());
		perform(get("/api/roles"), subAdmin, null).andExpect(status().isForbidden());
		perform(get("/api/functions"), subAdmin, null).andExpect(status().isForbidden());

		// Roles and grants follow ROLE_MANAGE: the admin, given write and delete through TEST_FLOW, manages them
		long roleManageId = 0;
		for (JsonNode fn : json(perform(get("/api/functions"), superUser, null))) {
			if (fn.get("functionCode").asString().equals("ROLE_MANAGE")) {
				roleManageId = fn.get("functionId").asLong();
			}
		}
		long grantId = json(perform(post("/api/roles/functions/add"), superUser, "{\"roleId\":" + testRoleId
				+ ",\"functionId\":" + roleManageId + ",\"canWrite\":true,\"canDelete\":true}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.canRead").value(true))).get("roleFunctionId").asLong();
		perform(post("/api/users/roles/assign"), superUser, "{\"userId\":" + adminId + ",\"roleId\":" + testRoleId + "}")
				.andExpect(status().isCreated());
		long otherRoleId = json(perform(post("/api/roles/create"), admin, "{\"code\":\"TEST_FLOW_2\",\"name\":\"Test Flow 2\"}")
				.andExpect(status().isCreated())).get("roleId").asLong();
		perform(put("/api/roles/update"), admin, "{\"roleId\":" + otherRoleId + ",\"name\":\"Renamed\"}")
				.andExpect(status().isOk());
		perform(get("/api/roles/" + otherRoleId), admin, null).andExpect(jsonPath("$.name").value("Renamed"));
		perform(delete("/api/roles/delete/" + otherRoleId), admin, null).andExpect(status().isNoContent());
		perform(get("/api/roles/functions/" + testRoleId), admin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].functionCode").value("ROLE_MANAGE"));

		// ... but never the SUPER_USER role, which only super users change
		Short superUserRole = roleRepository.findByCode("SUPER_USER").orElseThrow().getId();
		perform(put("/api/roles/update"), admin, "{\"roleId\":" + superUserRole + ",\"activeFlag\":false}")
				.andExpect(status().isForbidden());
		perform(post("/api/roles/functions/add"), admin, "{\"roleId\":" + superUserRole + ",\"functionId\":"
				+ roleManageId + ",\"canRead\":true}")
				.andExpect(status().isForbidden());

		// Write without delete: grants change, but neither roles nor grants are deleted
		perform(put("/api/roles/functions/update"), superUser, "{\"roleFunctionId\":" + grantId + ",\"canDelete\":false}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canDelete").value(false));
		perform(delete("/api/roles/functions/delete/" + grantId), admin, null).andExpect(status().isForbidden());
		perform(get("/api/roles/functions/" + testRoleId), subAdmin, null).andExpect(status().isForbidden());
		perform(get("/api/roles/32000"), superUser, null).andExpect(status().isNotFound());

		// What the console shows: the caller's own permissions, merged over their active roles
		perform(get("/api/users/me/permissions"), subAdmin, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isSuperUser").value(false))
				.andExpect(jsonPath("$.functions[?(@.functionCode == 'ADMIN_MANAGE')].canRead").value(true))
				.andExpect(jsonPath("$.functions[?(@.functionCode == 'ADMIN_MANAGE')].canWrite").value(false))
				.andExpect(jsonPath("$.functions[?(@.functionCode == 'ROLE_MANAGE')]").isEmpty());
		// The admin also holds TEST_FLOW, which grants ROLE_MANAGE write and read (delete was switched off above)
		perform(get("/api/users/me/permissions"), admin, null)
				.andExpect(jsonPath("$.functions[?(@.functionCode == 'ROLE_MANAGE')].canWrite").value(true))
				.andExpect(jsonPath("$.functions[?(@.functionCode == 'ROLE_MANAGE')].canDelete").value(false))
				.andExpect(jsonPath("$.functions[?(@.functionCode == 'LOOKUP_MANAGE')].canWrite").value(false));
		perform(get("/api/users/me/permissions"), superUser, null)
				.andExpect(jsonPath("$.isSuperUser").value(true))
				.andExpect(jsonPath("$.functions[?(@.functionCode == 'ROLE_MANAGE')].canDelete").value(true));
		mockMvc.perform(get("/api/users/me/permissions")).andExpect(status().isUnauthorized());

		// No write access of an admin includes making someone a super user
		perform(post("/api/users/roles/assign"), admin,
				"{\"userId\":" + adminId + ",\"roleId\":" + superUserRoleId + "}")
				.andExpect(status().isForbidden());

		// Users read and update themselves; other accounts need their role's function
		perform(get("/api/users/" + adminId), admin, null).andExpect(status().isOk());
		// The system account (a super user, or no role in some dev databases) is for super users only
		perform(get("/api/users/1"), admin, null).andExpect(status().isForbidden());
		perform(get("/api/users/1"), superUser, null).andExpect(status().isOk());

		// A blocked admin is signed out at once: their session ends, so the token no longer works
		perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/status/update"),
				superUser, "{\"userId\":" + adminId + ",\"accountStatus\":\"BLOCKED\"}")
				.andExpect(status().isOk());
		expectSessionEnded(admin);
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

	/** OTPs are random and only stored hashed, so the pending one is given a known value first. */
	private String verifyBody(String mobile, String purpose) {
		NexHomeAuthOtpRequest pending = otpRequestRepository
				.findByCountryCodeAndMobileNumberAndPurposeAndRequestStatus("+91", mobile, purpose, "PENDING")
				.orElseThrow();
		entityManager.createNativeQuery("update nexhome_auth_otp_requests set otp_hash = :hash where id = :id")
				.setParameter("hash", passwordEncoder.encode(KNOWN_OTP))
				.setParameter("id", pending.getId())
				.executeUpdate();
		entityManager.clear();
		return "{\"countryCode\":\"+91\",\"mobileNumber\":\"" + mobile + "\",\"purpose\":\"" + purpose
				+ "\",\"otp\":\"" + KNOWN_OTP + "\"}";
	}

	private static String refreshBody(String refreshToken) {
		return "{\"refreshToken\":\"" + refreshToken + "\"}";
	}

	/** An ACTIVE account with one role, created inside the test's transaction. */
	private NexHomeUser newUser(String roleCode) {
		NexHomeUser user = userRepository.save(userMapper.toUserEntity("+91", randomMobile(), null, null, 1L));
		userRoleRepository.saveAndFlush(userMapper.toUserRoleEntity(user,
				roleRepository.findByCode(roleCode).orElseThrow(), 1L));
		return user;
	}

	/**
	 * A login session and its access token, as the auth service would issue them, for users that cannot receive an
	 * OTP in tests.
	 */
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

	private static String randomLetters(int count) {
		StringBuilder letters = new StringBuilder();
		for (int i = 0; i < count; i++) {
			letters.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
		}
		return letters.toString();
	}

	private static String randomMobile() {
		return "8" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
	}
}
