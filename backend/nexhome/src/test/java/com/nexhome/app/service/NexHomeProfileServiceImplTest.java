package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.nexhome.app.dto.NexHomeAdminProfileCreateRequest;
import com.nexhome.app.dto.NexHomeAdminProfileResponse;
import com.nexhome.app.dto.NexHomeAdminProfileUpdateRequest;
import com.nexhome.app.dto.NexHomeCustomerProfileCreateRequest;
import com.nexhome.app.dto.NexHomeCustomerProfileResponse;
import com.nexhome.app.dto.NexHomeCustomerProfileUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeAdminProfileMapper;
import com.nexhome.app.mapper.NexHomeCustomerProfileMapper;
import com.nexhome.app.model.NexHomeAdminProfile;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAdminProfileRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class NexHomeProfileServiceImplTest {

	private static final Long ACTING_USER_ID = 1L;
	private static final Long OWNER_ID = 10L;

	private NexHomeCustomerProfileRepository customerProfileRepository;
	private NexHomeAdminProfileRepository adminProfileRepository;
	private NexHomeUserRepository userRepository;
	private NexHomeUserRoleRepository userRoleRepository;
	private NexHomeLookupService lookupService;
	private NexHomePermissionService permissionService;
	private NexHomeProfileServiceImpl profileService;

	@BeforeEach
	void setUp() {
		customerProfileRepository = mock(NexHomeCustomerProfileRepository.class);
		adminProfileRepository = mock(NexHomeAdminProfileRepository.class);
		userRepository = mock(NexHomeUserRepository.class);
		userRoleRepository = mock(NexHomeUserRoleRepository.class);
		lookupService = mock(NexHomeLookupService.class);
		permissionService = mock(NexHomePermissionService.class);
		when(customerProfileRepository.save(any())).thenAnswer(invocation -> {
			NexHomeCustomerProfile profile = invocation.getArgument(0);
			profile.setId(50L);
			return profile;
		});
		when(adminProfileRepository.save(any())).thenAnswer(invocation -> {
			NexHomeAdminProfile profile = invocation.getArgument(0);
			profile.setId(60L);
			return profile;
		});
		when(adminProfileRepository.findByEmployeeCode(any())).thenReturn(Optional.empty());
		when(lookupService.validateCode(eq("PREFERRED_CONTACT_LANGUAGE"), any(), eq("Preferred language")))
				.thenAnswer(invocation -> invocation.<String>getArgument(1).trim());
		// The caller is user 1 unless a test says otherwise
		when(permissionService.currentUserId()).thenReturn(ACTING_USER_ID);
		when(permissionService.isSelfOrCan(any(), any(), any())).thenReturn(true);
		profileService = new NexHomeProfileServiceImpl(customerProfileRepository, adminProfileRepository,
				userRepository, userRoleRepository, lookupService, permissionService,
				new NexHomeCustomerProfileMapper(), new NexHomeAdminProfileMapper());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Customer profiles
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void customerProfileValidatesLanguageAndTreatsBlankAsNone() {
		customerUser();

		NexHomeCustomerProfileResponse response = profileService.createCustomerProfile(
				new NexHomeCustomerProfileCreateRequest(OWNER_ID, " Ravi  Teja ", "Kumar", null, " te ", null));

		assertThat(response.firstName()).isEqualTo("Ravi Teja");
		assertThat(response.preferredLanguage()).isEqualTo("te");
		assertThat(response.marketingConsent()).isFalse();

		when(customerProfileRepository.findByUserId(OWNER_ID)).thenReturn(Optional.empty());
		NexHomeCustomerProfileResponse blank = profileService.createCustomerProfile(
				new NexHomeCustomerProfileCreateRequest(OWNER_ID, "Ravi", "Kumar", null, "   ", true));
		assertThat(blank.preferredLanguage()).isNull();
		assertThat(blank.marketingConsent()).isTrue();
		verify(lookupService, never()).validateCode(any(), eq("   "), any());
	}

	@Test
	void createFillsInTheEmptyProfileOfTheAccount() {
		customerUser();
		NexHomeCustomerProfile empty = new NexHomeCustomerProfile();
		empty.setId(70L);
		empty.setPublicId("NHCUST0070");
		empty.setUser(user(OWNER_ID));
		when(customerProfileRepository.findByUserId(OWNER_ID)).thenReturn(Optional.of(empty));

		NexHomeCustomerProfileResponse response = profileService.createCustomerProfile(
				new NexHomeCustomerProfileCreateRequest(OWNER_ID, " Ravi  Teja ", "Kumar", null, null, true));

		// The customer id stays; the empty profile is updated in place, not saved as a second one
		assertThat(response.publicId()).isEqualTo("NHCUST0070");
		assertThat(empty.getFirstName()).isEqualTo("Ravi Teja");
		assertThat(empty.isMarketingConsent()).isTrue();
		verify(customerProfileRepository, never()).save(any());
	}

	@Test
	void customerProfileNeedsCustomerRoleAndOnlyOnePerAccount() {
		NexHomeUser user = user(OWNER_ID);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> profileService.createCustomerProfile(
				new NexHomeCustomerProfileCreateRequest(OWNER_ID, "Ravi", "Kumar", null, null, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Customer profiles are only for accounts with the CUSTOMER role.");

		// A profile with a name already is a duplicate
		customerUser();
		NexHomeCustomerProfile named = new NexHomeCustomerProfile();
		named.setFirstName("Ravi");
		when(customerProfileRepository.findByUserId(OWNER_ID)).thenReturn(Optional.of(named));
		assertThatThrownBy(() -> profileService.createCustomerProfile(
				new NexHomeCustomerProfileCreateRequest(OWNER_ID, "Ravi", "Kumar", null, null, null)))
				.isInstanceOf(DuplicateResourceException.class);
		verify(customerProfileRepository, never()).save(any());

		when(userRepository.findById(99L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> profileService.createCustomerProfile(
				new NexHomeCustomerProfileCreateRequest(99L, "Ravi", "Kumar", null, null, null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not exist.");
	}

	@Test
	void customerProfileUpdateKeepsLanguageWhenMissing() {
		NexHomeCustomerProfile profile = existingCustomerProfile();
		profile.setPreferredLanguage("te");

		profileService.updateCustomerProfile(new NexHomeCustomerProfileUpdateRequest(50L, null, null, null, "Varma",
				null, null, true));

		assertThat(profile.getPreferredLanguage()).isEqualTo("te");
		assertThat(profile.getLastName()).isEqualTo("Varma");
		assertThat(profile.isMarketingConsent()).isTrue();
		assertThat(profile.getUpdatedBy()).isEqualTo(ACTING_USER_ID);
	}

	@Test
	void customerProfileOfSomeoneElseNeedsProfileManage() {
		NexHomeCustomerProfile profile = existingCustomerProfile();
		when(permissionService.isSelfOrCan(OWNER_ID, "CUSTOMER_MANAGE", "WRITE")).thenReturn(false);
		when(permissionService.isSelfOrCan(OWNER_ID, "CUSTOMER_MANAGE", "READ")).thenReturn(false);

		assertThatThrownBy(() -> profileService.updateCustomerProfile(new NexHomeCustomerProfileUpdateRequest(50L,
				null, null, null, "Varma", null, null, null)))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(profile.getLastName()).isEqualTo("Kumar");
		assertThatThrownBy(() -> profileService.getCustomerProfile(50L)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void currentCustomerProfileIsNotFoundWithoutOne() {
		when(customerProfileRepository.findByUserId(ACTING_USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> profileService.getCurrentCustomerProfile())
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("You do not have a customer profile yet.");
	}

	// ------------------------------------------------------------------------------------------------------------
	// Staff profiles
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void staffCreateTheirOwnProfileWithoutEmployeeDetails() {
		staffUser();
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);

		NexHomeAdminProfileResponse response = profileService.createAdminProfile(staffCreate(null, "500034", null));

		assertThat(response.adminProfileId()).isEqualTo(60L);
		assertThat(response.countryCode()).isEqualTo("IN");
		verify(permissionService, never()).can(anyString(), anyString());
	}

	@Test
	void employeeDetailsNeedStaffRoleWriteEvenForOwnProfile() {
		staffUser();
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);

		assertThatThrownBy(() -> profileService.createAdminProfile(staffCreate("emp-1", "500034", null)))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("No write access to this staff profile's employee details.");
		verify(adminProfileRepository, never()).save(any());

		when(permissionService.canManageStaff(OWNER_ID, "WRITE")).thenReturn(true);
		assertThat(profileService.createAdminProfile(staffCreate("emp-1", "500034", null)).employeeCode())
				.isEqualTo("EMP-1");
	}

	@Test
	void someoneElsesStaffProfileNeedsStaffRoleWrite() {
		staffUser();

		assertThatThrownBy(() -> profileService.createAdminProfile(staffCreate(null, "500034", null)))
				.isInstanceOf(AccessDeniedException.class);

		when(permissionService.canManageStaff(OWNER_ID, "WRITE")).thenReturn(true);
		assertThatCode(() -> profileService.createAdminProfile(staffCreate(null, "500034", null)))
				.doesNotThrowAnyException();
	}

	@Test
	void onlySuperUserChangesSuperUsersStaffProfile() {
		// canManageStaff is false for a super user's account unless the caller is a super user
		NexHomeAdminProfile profile = existingAdminProfile();

		assertThatThrownBy(() -> profileService.updateAdminProfile(staffUpdate(null, "Guntur")))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(profile.getCity()).isEqualTo("Hyderabad");

		// The super user may still change their own profile
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);
		profileService.updateAdminProfile(staffUpdate(null, "Guntur"));
		assertThat(profile.getCity()).isEqualTo("Guntur");

		// And another super user may change it
		when(permissionService.currentUserId()).thenReturn(ACTING_USER_ID);
		when(permissionService.canManageStaff(OWNER_ID, "WRITE")).thenReturn(true);
		profileService.updateAdminProfile(staffUpdate(null, "Vijayawada"));
		assertThat(profile.getCity()).isEqualTo("Vijayawada");
	}

	@Test
	void duplicateEmployeeCodeIsRejectedBeforeTheProfileChanges() {
		NexHomeAdminProfile profile = existingAdminProfile();
		profile.setEmployeeCode("EMP-1");
		when(permissionService.canManageStaff(OWNER_ID, "WRITE")).thenReturn(true);
		NexHomeAdminProfile other = new NexHomeAdminProfile();
		other.setId(61L);
		when(adminProfileRepository.findByEmployeeCode("EMP-2")).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> profileService.updateAdminProfile(staffUpdate(" emp-2 ", "Guntur")))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Another staff profile has this employee code.");
		assertThat(profile.getEmployeeCode()).isEqualTo("EMP-1");
		assertThat(profile.getCity()).isEqualTo("Hyderabad");

		// Its own code again is not a conflict
		when(adminProfileRepository.findByEmployeeCode("EMP-1")).thenReturn(Optional.of(profile));
		profileService.updateAdminProfile(staffUpdate("emp-1", "Guntur"));
		assertThat(profile.getCity()).isEqualTo("Guntur");
	}

	@Test
	void duplicateEmployeeCodeOnCreateIsRejected() {
		staffUser();
		when(permissionService.canManageStaff(OWNER_ID, "WRITE")).thenReturn(true);
		NexHomeAdminProfile other = new NexHomeAdminProfile();
		other.setId(61L);
		when(adminProfileRepository.findByEmployeeCode("EMP-2")).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> profileService.createAdminProfile(staffCreate("emp-2", "500034", null)))
				.isInstanceOf(DuplicateResourceException.class);
		verify(adminProfileRepository, never()).save(any());
	}

	@Test
	void staffProfileOfSomeoneElseNeedsWriteOnTheirStaffRole() {
		NexHomeAdminProfile profile = existingAdminProfile();
		when(permissionService.canManageStaff(OWNER_ID, "WRITE")).thenReturn(false);

		assertThatThrownBy(() -> profileService.updateAdminProfile(staffUpdate(null, "Guntur")))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(profile.getCity()).isEqualTo("Hyderabad");
	}

	@Test
	void postalCodeIsAPinCodeOnlyForIndia() {
		staffUser();
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);

		assertThatThrownBy(() -> profileService.createAdminProfile(staffCreate(null, "012345", null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Postal code must be a 6-digit PIN code for India.");
		assertThatThrownBy(() -> profileService.createAdminProfile(staffCreate(null, "SW1A 1AA", "in")))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(profileService.createAdminProfile(staffCreate(null, "sw1a 1aa", "gb")))
				.satisfies(created -> {
					assertThat(created.countryCode()).isEqualTo("GB");
					assertThat(created.postalCode()).isEqualTo("SW1A 1AA");
				});

		assertThatCode(() -> NexHomeProfileServiceImpl.requirePostalCodeForCountry("US", "94105"))
				.doesNotThrowAnyException();
		assertThatThrownBy(() -> NexHomeProfileServiceImpl.requirePostalCodeForCountry("IN", "50003"))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void changingCountryToIndiaChecksTheExistingPostalCode() {
		NexHomeAdminProfile profile = existingAdminProfile();
		profile.setCountryCode("GB");
		profile.setPostalCode("SW1A 1AA");
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);

		assertThatThrownBy(() -> profileService.updateAdminProfile(new NexHomeAdminProfileUpdateRequest(60L, null,
				null, null, null, null, null, null, null, null, null, null, null, null, "IN")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Postal code must be a 6-digit PIN code for India.");
	}

	@Test
	void staffProfileIsOnlyForStaffRoles() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID)));

		assertThatThrownBy(() -> profileService.createAdminProfile(staffCreate(null, "500034", null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Staff profiles are only for super users, admins and sub-admins.");
	}

	// ------------------------------------------------------------------------------------------------------------
	// Request validation
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void namesAcceptIndianScriptsButNotDigits() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(new NexHomeCustomerProfileCreateRequest(OWNER_ID,
					"రవి తేజ", "शर्मा", LocalDate.of(1990, 1, 1), null, null))).isEmpty();
			assertThat(factory.getValidator().validate(new NexHomeCustomerProfileCreateRequest(OWNER_ID,
					"Ravi2", "O'Brien-Kumar", LocalDate.now().plusDays(1), null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"First name must contain only letters, spaces, dots, apostrophes and hyphens",
							"Date of birth must be in the past");
		}
	}

	@Test
	void updateRequestsRejectOwnerAndPublicId() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(new NexHomeCustomerProfileUpdateRequest(50L, 2L, "NHCUST0001",
					null, null, null, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("User cannot be updated", "Public ID cannot be updated");
			assertThat(factory.getValidator().validate(new NexHomeAdminProfileUpdateRequest(60L, null, null, null,
					"Sharma", "emp 1", null, null, " ", null, null, null, null, "IND", "india")))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Employee code must contain only letters, digits, hyphens and underscores",
							"Address line 1 cannot be blank",
							"Country code must be a 2-letter ISO country code, e.g. IN");
		}
	}

	private NexHomeUser user(Long id) {
		NexHomeUser user = new NexHomeUser();
		user.setId(id);
		user.setCountryCode("+91");
		user.setMobileNumber("9876543210");
		return user;
	}

	private void customerUser() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID)));
		when(userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(OWNER_ID, "CUSTOMER"))
				.thenReturn(true);
	}

	private void staffUser() {
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID)));
		when(userRoleRepository.existsByUserIdAndRoleCodeInAndActiveTrueAndRoleActiveFlagTrue(eq(OWNER_ID), any()))
				.thenReturn(true);
	}

	private NexHomeCustomerProfile existingCustomerProfile() {
		NexHomeCustomerProfile profile = new NexHomeCustomerProfileMapper().toCustomerProfileEntity(
				new NexHomeCustomerProfileCreateRequest(OWNER_ID, "Ravi", "Kumar", null, null, null), user(OWNER_ID),
				OWNER_ID);
		profile.setId(50L);
		when(customerProfileRepository.findById(50L)).thenReturn(Optional.of(profile));
		return profile;
	}

	private NexHomeAdminProfile existingAdminProfile() {
		NexHomeAdminProfile profile = new NexHomeAdminProfileMapper().toAdminProfileEntity(
				staffCreate(null, "500034", null), user(OWNER_ID), OWNER_ID);
		profile.setId(60L);
		when(adminProfileRepository.findById(60L)).thenReturn(Optional.of(profile));
		return profile;
	}

	private static NexHomeAdminProfileCreateRequest staffCreate(String employeeCode, String postalCode,
			String countryCode) {
		return new NexHomeAdminProfileCreateRequest(OWNER_ID, "Priya", "Sharma", employeeCode, null, null,
				"Flat 4, Banjara Hills", null, null, "Hyderabad", "Telangana", postalCode, countryCode);
	}

	private static NexHomeAdminProfileUpdateRequest staffUpdate(String employeeCode, String city) {
		return new NexHomeAdminProfileUpdateRequest(60L, null, null, null, null, employeeCode, null, null, null, null,
				null, city, null, null, null);
	}
}
