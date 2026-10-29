package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserRoleAssignRequest;
import com.nexhome.app.dto.NexHomeUserRoleUpdateRequest;
import com.nexhome.app.dto.NexHomeUserStatsResponse;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeAdminProfileMapper;
import com.nexhome.app.mapper.NexHomeCustomerProfileMapper;
import com.nexhome.app.mapper.NexHomePartnerProfileMapper;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeAdminProfile;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserRole;
import com.nexhome.app.model.NexHomeUserRoleId;
import com.nexhome.app.repository.NexHomeAdminProfileRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerTimeOffRepository;
import com.nexhome.app.repository.NexHomeAuthRefreshTokenRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class NexHomeUserServiceImplTest {

	private static final Short CUSTOMER_ROLE_ID = 4;
	private static final Short PARTNER_ROLE_ID = 5;

	private NexHomeUserRepository userRepository;
	private NexHomeRoleRepository roleRepository;
	private NexHomeUserRoleRepository userRoleRepository;
	private NexHomeLookupService lookupService;
	private NexHomeAuthRefreshTokenRepository refreshTokenRepository;
	private NexHomePermissionService permissionService;
	private NexHomeAdminProfileRepository adminProfileRepository;
	private NexHomeCustomerProfileRepository customerProfileRepository;
	private NexHomePartnerProfileRepository partnerProfileRepository;
	private NexHomePartnerProfileMapper partnerProfileMapper;
	private NexHomeUserServiceImpl userService;

	@BeforeEach
	void setUp() {
		userRepository = mock(NexHomeUserRepository.class);
		roleRepository = mock(NexHomeRoleRepository.class);
		userRoleRepository = mock(NexHomeUserRoleRepository.class);
		lookupService = mock(NexHomeLookupService.class);
		when(userRepository.findByCountryCodeAndMobileNumber(any(), any())).thenReturn(Optional.empty());
		when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
		when(userRepository.save(any())).thenAnswer(invocation -> {
			NexHomeUser user = invocation.getArgument(0);
			user.setId(10L);
			return user;
		});
		when(userRoleRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(lookupService.validateCode(eq("ACCOUNT_STATUS"), any(), eq("Account status")))
				.thenAnswer(invocation -> invocation.<String>getArgument(1).trim());
		refreshTokenRepository = mock(NexHomeAuthRefreshTokenRepository.class);
		permissionService = mock(NexHomePermissionService.class);
		adminProfileRepository = mock(NexHomeAdminProfileRepository.class);
		customerProfileRepository = mock(NexHomeCustomerProfileRepository.class);
		when(customerProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		// A partner's empty DRAFT (V16)
		partnerProfileRepository = mock(NexHomePartnerProfileRepository.class);
		when(partnerProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		partnerProfileMapper = mock(NexHomePartnerProfileMapper.class);
		when(partnerProfileMapper.toPartnerProfileEntity(any(), any())).thenAnswer(invocation -> {
			NexHomePartnerProfile profile = new NexHomePartnerProfile();
			profile.setUser(invocation.getArgument(0));
			return profile;
		});
		when(adminProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		// The caller is user 1 unless a test says otherwise, with access to accounts of every role but SUPER_USER
		// (which the service also checks with isSuperUser, false unless a test says otherwise)
		when(permissionService.currentUserId()).thenReturn(1L);
		when(permissionService.canManageRole(any(), anyString())).thenAnswer(invocation -> {
			String roleCode = invocation.getArgument(0);
			return roleCode != null && !"SUPER_USER".equals(roleCode);
		});
		when(permissionService.canManageAccount(any(), anyString())).thenReturn(true);
		userService = new NexHomeUserServiceImpl(userRepository, roleRepository, userRoleRepository,
				refreshTokenRepository, lookupService, permissionService, mock(NexHomeAuditRecorder.class),
				new NexHomeUserMapper(),
				adminProfileRepository, new NexHomeAdminProfileMapper(), customerProfileRepository, new NexHomeCustomerProfileMapper(),
				partnerProfileRepository, mock(NexHomePartnerTimeOffRepository.class), partnerProfileMapper);
	}

	@Test
	void listsUsersOfRoleWithTheirRolesInOneQuery() {
		NexHomeUser user = existingUser();
		NexHomeRole customer = activeRole(CUSTOMER_ROLE_ID, "CUSTOMER");
		NexHomeRole partner = activeRole(PARTNER_ROLE_ID, "PARTNER");
		NexHomeUserMapper mapper = new NexHomeUserMapper();
		when(userRepository.findByActiveRole(eq("CUSTOMER"), eq("ACTIVE"), eq("ravi@"), any()))
				.thenReturn(new PageImpl<>(List.of(user), PageRequest.of(1, 5), 6));
		when(userRoleRepository.findWithRoleByUserIdIn(List.of(10L))).thenReturn(List.of(
				mapper.toUserRoleEntity(user, customer, 1L), mapper.toUserRoleEntity(user, partner, 1L)));

		NexHomePageResponse<NexHomeUserResponse> page = userService.getUsersByRole("CUSTOMER", 1, 5, " ACTIVE ",
				" Ravi@ ");

		assertThat(page.page()).isEqualTo(1);
		assertThat(page.size()).isEqualTo(5);
		assertThat(page.totalElements()).isEqualTo(6);
		assertThat(page.totalPages()).isEqualTo(2);
		assertThat(page.content()).singleElement().satisfies(listed ->
				assertThat(listed.roles()).extracting("roleCode").containsExactly("CUSTOMER", "PARTNER"));
		verify(userRoleRepository, never()).findByUserIdOrderByRoleIdAsc(any());
	}

	@Test
	void statsRunTotalsOverTheLastTenMonths() {
		YearMonth now = YearMonth.now(ZoneId.of("Asia/Kolkata"));
		when(userRoleRepository.countActiveByRoleAndMonth("CUSTOMER")).thenReturn(List.of(
				monthlyCount(now.minusMonths(14), 5), monthlyCount(now.minusMonths(1), 2), monthlyCount(now, 3)));
		when(userRoleRepository.countActiveByRoleAndMonth("PARTNER")).thenReturn(List.of());

		NexHomeUserStatsResponse stats = userService.getUserStats();

		NexHomeUserStatsResponse.RoleStats customers = stats.customers();
		assertThat(customers.total()).isEqualTo(10);
		assertThat(customers.addedThisMonth()).isEqualTo(3);
		assertThat(customers.months()).hasSize(10);
		// Older additions are the starting total of the window
		assertThat(customers.months().getFirst())
				.isEqualTo(new NexHomeUserStatsResponse.MonthStats(now.minusMonths(9).toString(), 0, 5));
		assertThat(customers.months().get(8))
				.isEqualTo(new NexHomeUserStatsResponse.MonthStats(now.minusMonths(1).toString(), 2, 7));
		assertThat(customers.months().getLast())
				.isEqualTo(new NexHomeUserStatsResponse.MonthStats(now.toString(), 3, 10));

		assertThat(stats.partners().total()).isZero();
		assertThat(stats.partners().months()).hasSize(10).allMatch(month -> month.total() == 0);
	}

	private static NexHomeUserRoleRepository.MonthlyCount monthlyCount(YearMonth month, long total) {
		return new NexHomeUserRoleRepository.MonthlyCount() {
			@Override
			public String getMonth() {
				return month.toString();
			}

			@Override
			public Long getTotal() {
				return total;
			}
		};
	}

	@Test
	void listDefaultsAndChecksPaging() {
		when(userRepository.findByActiveRole(any(), any(), any(), any())).thenReturn(Page.empty());

		NexHomePageResponse<NexHomeUserResponse> page = userService.getUsersByRole("ADMIN", null, null, null, "  ");

		assertThat(page.page()).isZero();
		assertThat(page.size()).isEqualTo(10);
		assertThat(page.content()).isEmpty();
		verify(userRepository).findByActiveRole(eq("ADMIN"), eq(null), eq(null), any());
		verify(userRoleRepository, never()).findWithRoleByUserIdIn(any());

		assertThatThrownBy(() -> userService.getUsersByRole("ADMIN", -1, 20, null, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Page must be 0 or more.");
		assertThatThrownBy(() -> userService.getUsersByRole("ADMIN", 0, 101, null, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Size must be between 1 and 100.");
	}

	@Test
	void onlySuperUserCanGrantSuperUser() {
		existingUser();
		activeRole((short) 1, "SUPER_USER");

		assertThatThrownBy(() -> userService.assignRole(new NexHomeUserRoleAssignRequest(10L, (short) 1)))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> userService.createUser(new NexHomeUserCreateRequest(
				"+91", "9000000001", null, null, (short) 1)))
				.isInstanceOf(AccessDeniedException.class);
		verify(userRoleRepository, never()).saveAndFlush(any());

		when(permissionService.isSuperUser(1L)).thenReturn(true);
		when(permissionService.canManageRole("SUPER_USER", "WRITE")).thenReturn(true);
		userService.assignRole(new NexHomeUserRoleAssignRequest(10L, (short) 1));
		verify(userRoleRepository).saveAndFlush(any());
	}

	@Test
	void accountsNeedWriteOnTheirRolesFunction() {
		existingUser();
		activeRole((short) 3, "SUB_ADMIN");
		when(permissionService.canManageRole("SUB_ADMIN", "WRITE")).thenReturn(false);

		// Creating a sub-admin, or giving the role, takes SUB_ADMIN_MANAGE write
		assertThatThrownBy(() -> userService.createUser(new NexHomeUserCreateRequest(
				"+91", "9000000001", null, null, (short) 3)))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> userService.assignRole(new NexHomeUserRoleAssignRequest(10L, (short) 3)))
				.isInstanceOf(AccessDeniedException.class);
		verify(userRoleRepository, never()).saveAndFlush(any());

		// The status follows the account's own role function (canManageAccount)
		when(permissionService.canManageAccount(10L, "WRITE")).thenReturn(false);
		assertThatThrownBy(() -> userService.updateUserStatus(new NexHomeUserStatusUpdateRequest(10L, "BLOCKED")))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void statusRulesProtectCallerAndSuperUsers() {
		existingUser();

		when(permissionService.currentUserId()).thenReturn(10L);
		assertThatThrownBy(() -> userService.updateUserStatus(new NexHomeUserStatusUpdateRequest(10L, "BLOCKED")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("You cannot change your own account status.");

		when(permissionService.currentUserId()).thenReturn(1L);
		when(permissionService.isSuperUser(10L)).thenReturn(true);
		assertThatThrownBy(() -> userService.updateUserStatus(new NexHomeUserStatusUpdateRequest(10L, "BLOCKED")))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void leavingActiveEndsAllSessions() {
		existingUser();

		userService.updateUserStatus(new NexHomeUserStatusUpdateRequest(10L, "BLOCKED"));

		verify(refreshTokenRepository).revokeAllByUserId(eq(10L), eq("ACCOUNT_STATUS_CHANGED"), any(), eq(1L));
	}

	@Test
	void reactivatingKeepsSessionsAlone() {
		existingUser().setAccountStatus("SUSPENDED");

		userService.updateUserStatus(new NexHomeUserStatusUpdateRequest(10L, "ACTIVE"));

		verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any(), any(), any());
	}

	@Test
	void createsActiveUnverifiedUserWithRole() {
		activeRole(CUSTOMER_ROLE_ID, "CUSTOMER");

		NexHomeUserResponse response = userService.createUser(new NexHomeUserCreateRequest(
				"+91", "9876543210", " Ravi@Example.COM ", null, CUSTOMER_ROLE_ID));

		assertThat(response.userId()).isEqualTo(10L);
		assertThat(response.countryCode()).isEqualTo("+91");
		assertThat(response.mobileNumber()).isEqualTo("9876543210");
		assertThat(response.email()).isEqualTo("ravi@example.com");
		assertThat(response.accountStatus()).isEqualTo("ACTIVE");
		assertThat(response.accountVerifiedDate()).isNull();
		assertThat(response.roles()).singleElement().satisfies(role -> {
			assertThat(role.roleCode()).isEqualTo("CUSTOMER");
			assertThat(role.isActive()).isTrue();
		});
		verify(userRoleRepository).saveAndFlush(any());
		// A customer gets the empty customer profile (customer id), not a staff profile
		verify(adminProfileRepository, never()).save(any());
		verify(customerProfileRepository).save(org.mockito.ArgumentMatchers.argThat(profile ->
				profile.getUser().getId().equals(10L) && profile.getFirstName() == null));
	}

	@Test
	void staffAccountsGetTheirStaffProfileAtOnce() {
		activeRole((short) 2, "ADMIN");

		NexHomeUserResponse response = userService.createUser(new NexHomeUserCreateRequest(
				"+91", "9876543210", "admin@nexhome.in", null, (short) 2));

		ArgumentCaptor<NexHomeAdminProfile> profile = ArgumentCaptor.forClass(NexHomeAdminProfile.class);
		verify(adminProfileRepository).save(profile.capture());
		assertThat(profile.getValue().getUser().getId()).isEqualTo(response.userId());
		// Names (and the address) are filled in later under My Profile
		assertThat(profile.getValue().getFirstName()).isNull();
		assertThat(profile.getValue().getCreatedBy()).isEqualTo(1L);
	}

	@Test
	void mobileNumberAndEmailMustBeUnused() {
		activeRole(CUSTOMER_ROLE_ID, "CUSTOMER");
		when(userRepository.findByCountryCodeAndMobileNumber("+91", "9876543210"))
				.thenReturn(Optional.of(new NexHomeUser()));

		assertThatThrownBy(() -> userService.createUser(new NexHomeUserCreateRequest(
				"+91", "9876543210", null, null, CUSTOMER_ROLE_ID)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("An account already exists with this mobile number");

		when(userRepository.findByEmail("ravi@example.com")).thenReturn(Optional.of(new NexHomeUser()));
		assertThatThrownBy(() -> userService.createUser(new NexHomeUserCreateRequest(
				"+91", "9000000000", "ravi@example.com", null, CUSTOMER_ROLE_ID)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("An account already exists with this email");
		verify(userRepository, never()).save(any());
	}

	@Test
	void createNeedsActiveRole() {
		when(roleRepository.findByIdAndActiveFlagTrue(CUSTOMER_ROLE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.createUser(new NexHomeUserCreateRequest(
				"+91", "9876543210", null, null, CUSTOMER_ROLE_ID)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Role Not Found or Inactive");
	}

	@Test
	void createRequestValidatesPhoneAndEmail() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			assertThat(validator.validate(new NexHomeUserCreateRequest("91", "98765", "name@host", "ftp://x", null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Country code must be a + followed by 1 to 4 digits",
							"Mobile number must be 6 to 15 digits",
							"Email must be a valid email address",
							"Profile photo URL must be an http or https URL",
							"Role is required");
		}
	}

	@Test
	void updateRequestRejectsMobileNumberStatusAndRole() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(
					new NexHomeUserUpdateRequest(10L, "+1", "5551234567", null, null, "BLOCKED", CUSTOMER_ROLE_ID)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Country code can only be changed with a phone change OTP",
							"Mobile number can only be changed with a phone change OTP",
							"Account status cannot be updated here",
							"Role cannot be updated here");
		}
	}

	@Test
	void updateUserKeepsMissingFieldsAndClearsEmptyOnes() {
		NexHomeUser user = existingUser();
		user.setEmail("old@example.com");
		user.setProfilePhotoUrl("https://cdn.example.com/a.png");

		userService.updateUser(new NexHomeUserUpdateRequest(10L, null, null, "", null, null, null));

		assertThat(user.getEmail()).isNull();
		assertThat(user.getProfilePhotoUrl()).isEqualTo("https://cdn.example.com/a.png");
		assertThat(user.getUpdatedBy()).isEqualTo(1L);
	}

	@Test
	void updateUserRejectsEmailOfAnotherAccount() {
		existingUser();
		when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(new NexHomeUser()));

		assertThatThrownBy(() -> userService.updateUser(
				new NexHomeUserUpdateRequest(10L, null, null, "Taken@example.com", null, null, null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("An account already exists with this email");
	}

	@Test
	void updateStatusRecordsWhoAndWhen() {
		NexHomeUser user = existingUser();

		NexHomeUserResponse response = userService.updateUserStatus(new NexHomeUserStatusUpdateRequest(10L,
				" SUSPENDED "));

		assertThat(response.accountStatus()).isEqualTo("SUSPENDED");
		assertThat(user.getStatusUpdatedBy()).isEqualTo(1L);
		assertThat(user.getStatusUpdatedDate()).isNotNull();
	}

	@Test
	void sameStatusOrUnknownStatusIsRejected() {
		existingUser();

		assertThatThrownBy(() -> userService.updateUserStatus(new NexHomeUserStatusUpdateRequest(10L, "ACTIVE")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("User is already ACTIVE.");

		when(lookupService.validateCode("ACCOUNT_STATUS", "ARCHIVED", "Account status"))
				.thenThrow(new InvalidRequestException("Account status must be one of: ACTIVE."));
		assertThatThrownBy(() -> userService.updateUserStatus(new NexHomeUserStatusUpdateRequest(10L, "ARCHIVED")))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void assignsSecondRole() {
		NexHomeUser user = existingUser();
		activeRole(PARTNER_ROLE_ID, "PARTNER");

		userService.assignRole(new NexHomeUserRoleAssignRequest(10L, PARTNER_ROLE_ID));

		verify(userRoleRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(userRole ->
				userRole.getUser() == user && userRole.getRole().getCode().equals("PARTNER")
						&& userRole.getId().equals(new NexHomeUserRoleId(10L, PARTNER_ROLE_ID))
						&& userRole.isActive() && userRole.getAssignedBy().equals(1L)));
		// Becoming a partner creates the empty DRAFT partner profile, with its partner id (V16)
		verify(partnerProfileRepository).save(org.mockito.ArgumentMatchers.argThat(profile -> profile.getUser() == user));
	}

	@Test
	void sameRoleCannotBeAssignedTwice() {
		existingUser();
		activeRole(CUSTOMER_ROLE_ID, "CUSTOMER");
		when(userRoleRepository.existsById(new NexHomeUserRoleId(10L, CUSTOMER_ROLE_ID))).thenReturn(true);

		assertThatThrownBy(() -> userService.assignRole(new NexHomeUserRoleAssignRequest(10L, CUSTOMER_ROLE_ID)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("User already has this role");
		verify(userRoleRepository, never()).saveAndFlush(any());
	}

	@Test
	void withdrawsAndRestoresRole() {
		NexHomeUser user = existingUser();
		NexHomeRole role = activeRole(CUSTOMER_ROLE_ID, "CUSTOMER");
		NexHomeUserRole userRole = new NexHomeUserMapper().toUserRoleEntity(user, role, 1L);
		when(userRoleRepository.findById(new NexHomeUserRoleId(10L, CUSTOMER_ROLE_ID)))
				.thenReturn(Optional.of(userRole));
		when(userRoleRepository.findByUserIdOrderByRoleIdAsc(10L)).thenReturn(List.of(userRole));

		NexHomeUserResponse response = userService.updateUserRole(
				new NexHomeUserRoleUpdateRequest(10L, CUSTOMER_ROLE_ID, false));
		assertThat(response.roles()).singleElement().satisfies(r -> assertThat(r.isActive()).isFalse());

		role.setActiveFlag(false);
		assertThatThrownBy(() -> userService.updateUserRole(
				new NexHomeUserRoleUpdateRequest(10L, CUSTOMER_ROLE_ID, true)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("An inactive role cannot be activated for a user.");
	}

	@Test
	void updatingMissingRoleAssignmentIsRejected() {
		assertThatThrownBy(() -> userService.updateUserRole(
				new NexHomeUserRoleUpdateRequest(10L, CUSTOMER_ROLE_ID, true)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not have this role.");
	}

	private NexHomeUser existingUser() {
		NexHomeUser user = new NexHomeUserMapper().toUserEntity("+91", "9876543210", null, null, 1L);
		user.setId(10L);
		when(userRepository.findById(10L)).thenReturn(Optional.of(user));
		when(userRoleRepository.findByUserIdOrderByRoleIdAsc(10L)).thenReturn(List.of());
		return user;
	}

	private NexHomeRole activeRole(Short roleId, String code) {
		NexHomeRole role = new NexHomeRole();
		role.setId(roleId);
		role.setCode(code);
		role.setName(code.charAt(0) + code.substring(1).toLowerCase());
		when(roleRepository.findByIdAndActiveFlagTrue(roleId)).thenReturn(Optional.of(role));
		return role;
	}
}
