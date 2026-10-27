package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeLookupTypeCreateRequest;
import com.nexhome.app.dto.NexHomeLookupValueCreateRequest;
import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomeLookupValueUpdateRequest;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserRoleAssignRequest;
import com.nexhome.app.mapper.NexHomeAuthOtpRequestMapper;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAuthOtpRequestRepository;
import com.nexhome.app.repository.NexHomeLookupValueRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

import jakarta.persistence.EntityManager;

/**
 * Runs against the dev database (migrated by Flyway) and rolls back. Checks what mocks cannot: the seed data, the
 * partial unique indexes, the composite user-role key and the JSONB column.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
// The services record the signed-in user; act as the seeded system user
@WithMockUser(username = "1")
class NexHomeIdentitySchemaTest {

	// Permissions are not what this class tests (NexHomeAuthFlowTest does): user 1 may do everything here, although
	// the dev database's system account may hold no role
	@MockitoBean
	private NexHomePermissionService permissionService;

	@Autowired
	private NexHomeLookupService lookupService;
	@Autowired
	private NexHomeUserService userService;
	@Autowired
	private NexHomeRoleRepository roleRepository;
	@Autowired
	private NexHomeUserRepository userRepository;
	@Autowired
	private NexHomeLookupValueRepository lookupValueRepository;
	@Autowired
	private NexHomeAuthOtpRequestRepository otpRequestRepository;
	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void actAsSystemUser() {
		when(permissionService.currentUserId()).thenReturn(1L);
		when(permissionService.canManageRole(any(), any())).thenReturn(true);
	}

	@Test
	void seedsRolesSystemUserAndLookups() {
		assertThat(roleRepository.findAll()).extracting("code")
				.contains("SUPER_USER", "ADMIN", "SUB_ADMIN", "CUSTOMER", "PARTNER");
		NexHomeUser system = userRepository.findById(1L).orElseThrow();
		assertThat(system.getMobileNumber()).isEqualTo("0000000000");
		assertThat(lookupService.getActiveLookupValues("ACCOUNT_STATUS")).extracting(NexHomeLookupValueResponse::valueCode)
				.containsExactly("ACTIVE", "SUSPENDED", "BLOCKED", "DEACTIVATED");
		assertThat(lookupService.getActiveLookupValues("BOOKING_STATUS")).first()
				.satisfies(value -> assertThat(value.displayName()).isEqualTo("Pending"));
	}

	@Test
	void userGetsSeveralRolesThroughUserRoles() {
		Short customer = roleRepository.findByCode("CUSTOMER").orElseThrow().getId();
		Short partner = roleRepository.findByCode("PARTNER").orElseThrow().getId();

		NexHomeUserResponse created = userService.createUser(
				new NexHomeUserCreateRequest("+91", "9123400001", "Test.User@Example.com", null, customer));
		NexHomeUserResponse response = userService.assignRole(
				new NexHomeUserRoleAssignRequest(created.userId(), partner));

		assertThat(created.roles()).singleElement().satisfies(role -> assertThat(role.assignedAt()).isNotNull());
		assertThat(response.roles()).extracting("roleCode").containsExactly("CUSTOMER", "PARTNER");
		assertThat(response.email()).isEqualTo("test.user@example.com");
	}

	@Test
	void emailIsUniqueIgnoringCase() {
		Short customer = roleRepository.findByCode("CUSTOMER").orElseThrow().getId();
		userService.createUser(new NexHomeUserCreateRequest("+91", "9123400002", "dup@example.com", null, customer));

		NexHomeUser other = userRepository.findById(1L).orElseThrow();
		other.setEmail("DUP@example.com");
		assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(Exception.class)
				.hasStackTraceContaining("ux_nexhome_users_email");
	}

	@Test
	void onlyOneDefaultValuePerType() {
		Long typeId = lookupService.createLookupType(
				new NexHomeLookupTypeCreateRequest("TEST_LANGUAGE", "Test Language", null, null, null)).lookupTypeId();
		NexHomeLookupValueResponse en = lookupService.addLookupValue(new NexHomeLookupValueCreateRequest(
				typeId, "en", "English", null, 1, null, true, Map.of("native", "English")));
		NexHomeLookupValueResponse te = lookupService.addLookupValue(new NexHomeLookupValueCreateRequest(
				typeId, "te", "Telugu", null, 2, null, true, null));

		entityManager.flush();
		entityManager.clear();
		assertThat(lookupValueRepository.findById(en.lookupValueId()).orElseThrow().isDefaultValue()).isFalse();
		assertThat(lookupValueRepository.findById(te.lookupValueId()).orElseThrow().isDefaultValue()).isTrue();
		assertThat(lookupService.getLookupTypeValues(typeId).get(0).metadata()).containsEntry("native", "English");

		lookupService.updateLookupValue(new NexHomeLookupValueUpdateRequest(
				en.lookupValueId(), null, null, null, null, null, null, true, null));
		entityManager.flush();
		assertThat(lookupService.getActiveLookupValues("TEST_LANGUAGE"))
				.extracting(NexHomeLookupValueResponse::valueCode, NexHomeLookupValueResponse::isDefault)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("en", true),
						org.assertj.core.groups.Tuple.tuple("te", false));
	}

	@Test
	void onlyOnePendingOtpPerNumberAndPurpose() {
		NexHomeUser system = userRepository.findById(1L).orElseThrow();
		NexHomeAuthOtpRequestMapper mapper = new NexHomeAuthOtpRequestMapper();
		Instant expiresAt = Instant.now().plusSeconds(300);
		otpRequestRepository.saveAndFlush(
				mapper.toAuthOtpRequestEntity("+91", "9123400003", "LOGIN", system, "hash", expiresAt, 1L));

		assertThatThrownBy(() -> otpRequestRepository.saveAndFlush(
				mapper.toAuthOtpRequestEntity("+91", "9123400003", "LOGIN", system, "hash", expiresAt, 1L)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
