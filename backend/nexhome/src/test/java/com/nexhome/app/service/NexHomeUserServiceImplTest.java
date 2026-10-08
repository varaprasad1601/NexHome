package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nexhome.app.dto.NexHomeUserAddressRequest;
import com.nexhome.app.dto.NexHomeUserAddressResponse;
import com.nexhome.app.dto.NexHomeUserAddressUpdateRequest;
import com.nexhome.app.dto.NexHomeUserDetailsRequest;
import com.nexhome.app.dto.NexHomeUserDetailsResponse;
import com.nexhome.app.dto.NexHomeUserDetailsUpdateRequest;
import com.nexhome.app.dto.NexHomeUserPasswordSetRequest;
import com.nexhome.app.dto.NexHomeUserPasswordUpdateRequest;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeUserAddressMapper;
import com.nexhome.app.mapper.NexHomeUserDetailsMapper;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserAddress;
import com.nexhome.app.model.NexHomeUserDetails;
import com.nexhome.app.model.NexHomeUserStatus;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserAddressRepository;
import com.nexhome.app.repository.NexHomeUserDetailsRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class NexHomeUserServiceImplTest {

	private static final String PASSWORD = "secret-pass-1";

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private NexHomeUserRepository userRepository;
	private NexHomeRoleRepository roleRepository;
	private NexHomeUserDetailsRepository userDetailsRepository;
	private NexHomeUserAddressRepository userAddressRepository;
	private NexHomeUserServiceImpl userService;

	@BeforeEach
	void setUp() {
		userRepository = mock(NexHomeUserRepository.class);
		roleRepository = mock(NexHomeRoleRepository.class);
		userDetailsRepository = mock(NexHomeUserDetailsRepository.class);
		userAddressRepository = mock(NexHomeUserAddressRepository.class);
		when(userRepository.findByPhoneAndRoleRoleId(any(), any())).thenReturn(Optional.empty());
		when(userRepository.save(any())).thenAnswer(invocation -> {
			NexHomeUser user = invocation.getArgument(0);
			user.setUserId(100L);
			return user;
		});
		when(userDetailsRepository.findByUserUserId(any())).thenReturn(Optional.empty());
		when(userDetailsRepository.save(any())).thenAnswer(invocation -> {
			NexHomeUserDetails details = invocation.getArgument(0);
			details.setUserDetailsId(50L);
			return details;
		});
		when(userAddressRepository.findByUserUserIdAndActiveFlag(any(), eq(true))).thenReturn(Optional.empty());
		when(userAddressRepository.save(any())).thenAnswer(invocation -> {
			NexHomeUserAddress address = invocation.getArgument(0);
			address.setUserAddressId(30L);
			return address;
		});
		userService = new NexHomeUserServiceImpl(userRepository, roleRepository, userDetailsRepository,
				userAddressRepository, new NexHomeUserMapper(), new NexHomeUserDetailsMapper(),
				new NexHomeUserAddressMapper(), passwordEncoder);
	}

	// ---- createUser ----

	@Test
	void createsSubmittedInactiveUserWithoutPassword() {
		givenRole(5L, "CUSTOMER", true);

		NexHomeUserResponse response = userService.createUser(request(" 9876543210 ", " Ravi@Example.com ", 5L));

		NexHomeUser user = savedUser();
		assertThat(user.getPhone()).isEqualTo("9876543210");
		assertThat(user.getEmail()).isEqualTo("ravi@example.com");
		assertThat(user.getPassword()).isNull();
		assertThat(user.getUserStatus()).isEqualTo(NexHomeUserStatus.SUBMITTED);
		assertThat(user.isActiveFlag()).isFalse();
		assertThat(response.userStatus()).isEqualTo(NexHomeUserStatus.SUBMITTED);
		assertThat(response.activeFlag()).isFalse();
		assertThat(user.getCreatedBy()).isEqualTo(1L);
		assertThat(user.getLastUpdateBy()).isEqualTo(1L);

		assertThat(response.userId()).isEqualTo(100L);
		assertThat(response.roleName()).isEqualTo("CUSTOMER");
	}

	@Test
	void blankEmailIsStoredAsNull() {
		givenRole(5L, "CUSTOMER", true);

		userService.createUser(request("9876543210", "  ", 5L));

		assertThat(savedUser().getEmail()).isNull();
	}

	@Test
	void samePhoneAndRoleIsRejected() {
		when(userRepository.findByPhoneAndRoleRoleId("9876543210", 5L)).thenReturn(Optional.of(new NexHomeUser()));

		assertThatThrownBy(() -> userService.createUser(request(" 9876543210 ", null, 5L)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("User already exists with this phone number");
		verify(userRepository, never()).save(any());
	}

	@Test
	void samePhoneWithDifferentRoleIsAllowed() {
		when(userRepository.findByPhoneAndRoleRoleId("9876543210", 5L)).thenReturn(Optional.of(new NexHomeUser()));
		givenRole(6L, "PARTNER", true);

		NexHomeUserResponse response = userService.createUser(request("9876543210", null, 6L));

		assertThat(response.roleName()).isEqualTo("PARTNER");
		assertThat(savedUser().getRole().getRoleId()).isEqualTo(6L);
	}

	@Test
	void unknownRoleIsRejected() {
		when(roleRepository.findByRoleIdAndActiveFlag(99L, true)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.createUser(request("9876543210", null, 99L)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Role Not Found or Inactive");
		verify(userRepository, never()).save(any());
	}

	@Test
	void inactiveRoleIsRejected() {
		givenRole(5L, "CUSTOMER", false);

		assertThatThrownBy(() -> userService.createUser(request("9876543210", null, 5L)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Role Not Found or Inactive");
		verify(userRepository, never()).save(any());
	}

	// ---- addUserDetails ----

	@Test
	void addsDetailsForExistingUser() {
		NexHomeUser user = givenUser(7L);

		NexHomeUserDetailsResponse response = userService.addUserDetails(new NexHomeUserDetailsRequest(
				7L, " Ravi ", "Kumar"));

		ArgumentCaptor<NexHomeUserDetails> saved = ArgumentCaptor.forClass(NexHomeUserDetails.class);
		verify(userDetailsRepository).save(saved.capture());
		NexHomeUserDetails details = saved.getValue();
		assertThat(details.getUser()).isSameAs(user);
		assertThat(details.getFirstName()).isEqualTo("Ravi");
		assertThat(details.getFullName()).isEqualTo("Ravi Kumar");
		assertThat(details.getCreatedBy()).isEqualTo(1L);
		assertThat(details.getLastUpdateBy()).isEqualTo(1L);

		assertThat(response.userDetailsId()).isEqualTo(50L);
		assertThat(response.userId()).isEqualTo(7L);
	}

	@Test
	void fullNameIsFirstNameWhenLastNameMissing() {
		givenUser(7L);

		NexHomeUserDetailsResponse response = userService.addUserDetails(detailsRequest(7L, "Asha"));

		assertThat(response.fullName()).isEqualTo("Asha");
	}

	@Test
	void detailsForUnknownUserAreRejected() {
		when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.addUserDetails(detailsRequest(99L, "Ravi")))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not exist.");
		verify(userDetailsRepository, never()).save(any());
	}

	@Test
	void secondDetailsRecordForSameUserIsRejected() {
		givenUser(7L);
		when(userDetailsRepository.findByUserUserId(7L)).thenReturn(Optional.of(new NexHomeUserDetails()));

		assertThatThrownBy(() -> userService.addUserDetails(detailsRequest(7L, "Ravi")))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("User details already exist for this user");
		verify(userDetailsRepository, never()).save(any());
	}

	// ---- addUserAddress ----

	@Test
	void addressWithoutActiveFlagIsSavedInactive() {
		NexHomeUser user = givenUser(7L);

		NexHomeUserAddressResponse response = userService.addUserAddress(addressRequest(7L, " Flat 4, MG Road ", null));

		NexHomeUserAddress saved = savedAddress();
		assertThat(saved.getUser()).isSameAs(user);
		assertThat(saved.getAddress1()).isEqualTo("Flat 4, MG Road");
		assertThat(saved.isActiveFlag()).isFalse();
		assertThat(saved.getCreatedBy()).isEqualTo(1L);
		assertThat(response.userAddressId()).isEqualTo(30L);
		assertThat(response.userId()).isEqualTo(7L);
		assertThat(response.activeFlag()).isFalse();
		// The current active address is not looked up, so it stays active
		verify(userAddressRepository, never()).findByUserUserIdAndActiveFlag(any(), anyBoolean());
		verify(userAddressRepository, never()).saveAndFlush(any());
	}

	@Test
	void explicitlyActiveAddressIsActiveWhenUserHasNoActiveAddress() {
		givenUser(7L);

		NexHomeUserAddressResponse response = userService.addUserAddress(addressRequest(7L, "Flat 4", true));

		assertThat(savedAddress().isActiveFlag()).isTrue();
		assertThat(response.activeFlag()).isTrue();
		verify(userAddressRepository, never()).saveAndFlush(any());
	}

	@Test
	void newActiveAddressDeactivatesCurrentOneFirst() {
		givenUser(7L);
		NexHomeUserAddress current = new NexHomeUserAddress();
		current.setActiveFlag(true);
		when(userAddressRepository.findByUserUserIdAndActiveFlag(7L, true)).thenReturn(Optional.of(current));

		userService.addUserAddress(addressRequest(7L, "New home", true));

		assertThat(current.isActiveFlag()).isFalse();
		assertThat(current.getLastUpdateBy()).isEqualTo(1L);
		// The deactivation is flushed before the new active address is inserted
		InOrder order = inOrder(userAddressRepository);
		order.verify(userAddressRepository).saveAndFlush(current);
		order.verify(userAddressRepository).save(any());
		assertThat(savedAddress().isActiveFlag()).isTrue();
	}

	@Test
	void inactiveAddressLeavesCurrentActiveAddressAlone() {
		givenUser(7L);

		NexHomeUserAddressResponse response = userService.addUserAddress(addressRequest(7L, "Office", false));

		assertThat(response.activeFlag()).isFalse();
		verify(userAddressRepository, never()).findByUserUserIdAndActiveFlag(any(), anyBoolean());
		verify(userAddressRepository, never()).saveAndFlush(any());
	}

	@Test
	void addressForUnknownUserIsRejected() {
		when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.addUserAddress(addressRequest(99L, "Flat 4", null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not exist.");
		verify(userAddressRepository, never()).save(any());
	}

	@Test
	void createRequestRejectsPassword() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			assertThat(validator.validate(new NexHomeUserCreateRequest("9876543210", null, 5L, "Secret@123")))
					.extracting(ConstraintViolation::getMessage)
					.containsExactly("Password cannot be set when creating a user");
			assertThat(validator.validate(request("9876543210", "ravi@example.com", 5L))).isEmpty();
		}
	}

	// ---- updateUser ----

	@Test
	void updateUserChangesEmailAndActiveFlagOnly() {
		NexHomeUser user = existingUser(7L, "9876543210", "old@example.com", 5L, "CUSTOMER");
		user.setPassword("existing-hash");

		NexHomeUserResponse response = userService.updateUser(userUpdate(7L, " New@Example.com ", false));

		assertThat(user.getEmail()).isEqualTo("new@example.com");
		assertThat(user.isActiveFlag()).isFalse();
		assertThat(user.getPassword()).isEqualTo("existing-hash");
		assertThat(user.getPhone()).isEqualTo("9876543210");
		assertThat(user.getRole().getRoleId()).isEqualTo(5L);
		assertThat(user.getLastUpdateBy()).isEqualTo(1L);
		assertThat(response.phone()).isEqualTo("9876543210");
		assertThat(response.roleName()).isEqualTo("CUSTOMER");
	}

	@Test
	void updateUserClearsEmailWhenBlank() {
		NexHomeUser user = existingUser(7L, "9876543210", "old@example.com", 5L, "CUSTOMER");

		userService.updateUser(userUpdate(7L, "", null));

		assertThat(user.getEmail()).isNull();
		assertThat(user.isActiveFlag()).isTrue();
	}

	@Test
	void updateUnknownUserIsRejected() {
		when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.updateUser(userUpdate(99L, null, null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not exist.");
	}

	@Test
	void updateRequestRejectsPhoneRoleAndPassword() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			assertThat(validator.validate(new NexHomeUserUpdateRequest(7L, "9123456780", 6L, null, null, "Secret@123")))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Phone cannot be updated", "Role cannot be updated",
							"Password cannot be updated here");
			assertThat(validator.validate(userUpdate(7L, "new@example.com", true))).isEmpty();
		}
	}

	// ---- deleteUser ----

	@Test
	void deleteUserRemovesAddressesAndDetailsBeforeTheUser() {
		existingUser(7L, "9876543210", null, 5L, "CUSTOMER");

		userService.deleteUser(7L);

		InOrder order = inOrder(userAddressRepository, userDetailsRepository, userRepository);
		order.verify(userAddressRepository).deleteAllByUserId(7L);
		order.verify(userDetailsRepository).deleteAllByUserId(7L);
		order.verify(userRepository).deleteById(7L);
	}

	@Test
	void deleteUnknownUserIsRejected() {
		when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.deleteUser(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not exist.");
		verify(userRepository, never()).deleteById(any());
	}

	// ---- updatePassword ----

	@Test
	void updatePasswordStoresNewHashWhenCurrentPasswordMatches() {
		NexHomeUser user = existingUser(7L, "9876543210", null, 5L, "CUSTOMER");
		user.setPassword(passwordEncoder.encode("OldSecret@1"));

		NexHomeUserResponse response = userService.updatePassword(
				new NexHomeUserPasswordUpdateRequest(7L, "OldSecret@1", "NewSecret@1"));

		assertThat(passwordEncoder.matches("NewSecret@1", user.getPassword())).isTrue();
		assertThat(passwordEncoder.matches("OldSecret@1", user.getPassword())).isFalse();
		assertThat(user.getLastUpdateBy()).isEqualTo(1L);
		assertThat(response.userId()).isEqualTo(7L);
	}

	@Test
	void updatePasswordRejectsWrongCurrentPassword() {
		NexHomeUser user = existingUser(7L, "9876543210", null, 5L, "CUSTOMER");
		String originalHash = passwordEncoder.encode("OldSecret@1");
		user.setPassword(originalHash);

		assertThatThrownBy(() -> userService.updatePassword(
				new NexHomeUserPasswordUpdateRequest(7L, "Wrong@123", "NewSecret@1")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Current password is incorrect.");
		assertThat(user.getPassword()).isEqualTo(originalHash);
	}

	@Test
	void updatePasswordRejectsSamePassword() {
		NexHomeUser user = existingUser(7L, "9876543210", null, 5L, "CUSTOMER");
		user.setPassword(passwordEncoder.encode("OldSecret@1"));

		assertThatThrownBy(() -> userService.updatePassword(
				new NexHomeUserPasswordUpdateRequest(7L, "OldSecret@1", "OldSecret@1")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("New password must be different from the current password.");
	}

	@Test
	void updatePasswordForUnknownUserIsRejected() {
		when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.updatePassword(
				new NexHomeUserPasswordUpdateRequest(99L, "OldSecret@1", "NewSecret@1")))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not exist.");
	}

	@Test
	void onlyActivatedUserCanBeMadeActive() {
		existingUserWithStatus(7L, NexHomeUserStatus.APPROVED);

		assertThatThrownBy(() -> userService.updateUser(userUpdate(7L, null, true)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Only an activated user can be made active.");
	}

	@Test
	void updatePasswordRejectsUserWithoutPassword() {
		existingUserWithStatus(7L, NexHomeUserStatus.APPROVED);

		assertThatThrownBy(() -> userService.updatePassword(
				new NexHomeUserPasswordUpdateRequest(7L, "OldSecret@1", "NewSecret@1")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Password has not been set yet.");
	}

	// ---- updateUserStatus ----

	@Test
	void adminApprovesSubmittedUser() {
		NexHomeUser user = existingUserWithStatus(7L, NexHomeUserStatus.SUBMITTED);

		NexHomeUserResponse response = userService.updateUserStatus(
				new NexHomeUserStatusUpdateRequest(7L, NexHomeUserStatus.APPROVED));

		assertThat(user.getUserStatus()).isEqualTo(NexHomeUserStatus.APPROVED);
		assertThat(user.isActiveFlag()).isFalse();
		assertThat(user.getLastUpdateBy()).isEqualTo(1L);
		assertThat(response.userStatus()).isEqualTo(NexHomeUserStatus.APPROVED);
	}

	@Test
	void adminCanRejectAndLaterApprove() {
		NexHomeUser user = existingUserWithStatus(7L, NexHomeUserStatus.SUBMITTED);

		userService.updateUserStatus(new NexHomeUserStatusUpdateRequest(7L, NexHomeUserStatus.REJECTED));
		assertThat(user.getUserStatus()).isEqualTo(NexHomeUserStatus.REJECTED);
		userService.updateUserStatus(new NexHomeUserStatusUpdateRequest(7L, NexHomeUserStatus.APPROVED));
		assertThat(user.getUserStatus()).isEqualTo(NexHomeUserStatus.APPROVED);
	}

	@Test
	void statusCanOnlyBeSetToApprovedOrRejected() {
		existingUserWithStatus(7L, NexHomeUserStatus.APPROVED);

		assertThatThrownBy(() -> userService.updateUserStatus(
				new NexHomeUserStatusUpdateRequest(7L, NexHomeUserStatus.ACTIVATED)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("User status can only be set to APPROVED or REJECTED.");
	}

	@Test
	void activatedUserStatusCannotBeChanged() {
		existingUser(7L, "9876543210", null, 5L, "CUSTOMER");

		assertThatThrownBy(() -> userService.updateUserStatus(
				new NexHomeUserStatusUpdateRequest(7L, NexHomeUserStatus.REJECTED)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("An activated user's status cannot be changed.");
	}

	@Test
	void settingTheSameStatusAgainIsRejected() {
		existingUserWithStatus(7L, NexHomeUserStatus.APPROVED);

		assertThatThrownBy(() -> userService.updateUserStatus(
				new NexHomeUserStatusUpdateRequest(7L, NexHomeUserStatus.APPROVED)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("User is already APPROVED.");
	}

	// ---- setPassword ----

	@Test
	void approvedUserSetsPasswordAndIsActivated() {
		NexHomeUser user = existingUserWithStatus(7L, NexHomeUserStatus.APPROVED);

		NexHomeUserResponse response = userService.setPassword(
				new NexHomeUserPasswordSetRequest(7L, "Secret@123", "Secret@123"));

		assertThat(passwordEncoder.matches("Secret@123", user.getPassword())).isTrue();
		assertThat(user.getUserStatus()).isEqualTo(NexHomeUserStatus.ACTIVATED);
		assertThat(user.isActiveFlag()).isTrue();
		// The user sets their own password
		assertThat(user.getLastUpdateBy()).isEqualTo(7L);
		assertThat(response.userStatus()).isEqualTo(NexHomeUserStatus.ACTIVATED);
		assertThat(response.activeFlag()).isTrue();
	}

	@Test
	void passwordCanOnlyBeSetAfterApproval() {
		NexHomeUser user = existingUserWithStatus(7L, NexHomeUserStatus.SUBMITTED);

		assertThatThrownBy(() -> userService.setPassword(new NexHomeUserPasswordSetRequest(7L, "Secret@123", "Secret@123")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("User is not approved yet.");
		assertThat(user.getPassword()).isNull();
		assertThat(user.isActiveFlag()).isFalse();
	}

	@Test
	void passwordCannotBeSetTwice() {
		existingUser(7L, "9876543210", null, 5L, "CUSTOMER");

		assertThatThrownBy(() -> userService.setPassword(new NexHomeUserPasswordSetRequest(7L, "Secret@123", "Secret@123")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Password is already set. Use the password update instead.");
	}

	@Test
	void mismatchedConfirmPasswordIsRejected() {
		NexHomeUser user = existingUserWithStatus(7L, NexHomeUserStatus.APPROVED);

		assertThatThrownBy(() -> userService.setPassword(new NexHomeUserPasswordSetRequest(7L, "Secret@123", "Secret@124")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Password and confirm password do not match.");
		assertThat(user.getUserStatus()).isEqualTo(NexHomeUserStatus.APPROVED);
	}

	// ---- updateUserDetails ----

	@Test
	void updateDetailsChangesFirstNameKeepsLastNameAndRecalculatesFullName() {
		NexHomeUserDetails details = existingDetails(7L, "Ravi", "Kumar");

		NexHomeUserDetailsResponse response = userService.updateUserDetails(
				new NexHomeUserDetailsUpdateRequest(50L, null, " Ravindra ", null));

		assertThat(details.getFirstName()).isEqualTo("Ravindra");
		assertThat(details.getLastName()).isEqualTo("Kumar");
		assertThat(details.getFullName()).isEqualTo("Ravindra Kumar");
		assertThat(details.getLastUpdateBy()).isEqualTo(1L);
		assertThat(response.fullName()).isEqualTo("Ravindra Kumar");
	}

	@Test
	void updateDetailsClearsLastNameWhenBlank() {
		NexHomeUserDetails details = existingDetails(7L, "Ravi", "Kumar");

		userService.updateUserDetails(new NexHomeUserDetailsUpdateRequest(50L, null, null, ""));

		assertThat(details.getLastName()).isNull();
		assertThat(details.getFullName()).isEqualTo("Ravi");
	}

	@Test
	void detailsUpdateRequestRejectsUserId() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			assertThat(validator.validate(new NexHomeUserDetailsUpdateRequest(50L, 8L, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactly("User cannot be updated");
			assertThat(validator.validate(new NexHomeUserDetailsUpdateRequest(50L, null, "Ravi", null))).isEmpty();
		}
	}

	@Test
	void updateMissingDetailsIsRejected() {
		when(userDetailsRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.updateUserDetails(new NexHomeUserDetailsUpdateRequest(99L, null, "Ravi", null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User details do not exist.");
	}

	// ---- updateUserAddress ----

	@Test
	void updateAddressKeepsMissingRequiredFieldsAndReplacesAddress3() {
		NexHomeUserAddress address = existingAddress(30L, 7L, true);
		address.setAddress3("Near City Mall");

		NexHomeUserAddressResponse response = userService.updateUserAddress(new NexHomeUserAddressUpdateRequest(
				30L, null, " New line 1 ", null, null, null, null, null, "500033", null));

		assertThat(address.getAddress1()).isEqualTo("New line 1");
		// Required fields left out keep their current values
		assertThat(address.getAddress2()).isEqualTo("MG Road");
		assertThat(address.getCity()).isEqualTo("Hyderabad");
		assertThat(address.getDistrict()).isEqualTo("Hyderabad");
		// The optional address3 has no null check, so leaving it out clears it
		assertThat(address.getAddress3()).isNull();
		assertThat(address.getPinCode()).isEqualTo("500033");
		assertThat(address.isActiveFlag()).isTrue();
		assertThat(address.getLastUpdateBy()).isEqualTo(1L);
		assertThat(response.activeFlag()).isTrue();
		verify(userAddressRepository, never()).saveAndFlush(any());
	}

	@Test
	void activatingAnAddressDeactivatesTheCurrentOneFirst() {
		NexHomeUserAddress address = existingAddress(30L, 7L, false);
		NexHomeUserAddress current = existingAddress(31L, 7L, true);
		when(userAddressRepository.findByUserUserIdAndActiveFlag(7L, true)).thenReturn(Optional.of(current));

		userService.updateUserAddress(activeFlagUpdate(30L, true));

		assertThat(current.isActiveFlag()).isFalse();
		verify(userAddressRepository).saveAndFlush(current);
		assertThat(address.isActiveFlag()).isTrue();
	}

	@Test
	void activatingTheAlreadyActiveAddressChangesNothingElse() {
		NexHomeUserAddress address = existingAddress(30L, 7L, true);

		userService.updateUserAddress(activeFlagUpdate(30L, true));

		assertThat(address.isActiveFlag()).isTrue();
		verify(userAddressRepository, never()).findByUserUserIdAndActiveFlag(any(), anyBoolean());
	}

	@Test
	void deactivatingAnAddress() {
		NexHomeUserAddress address = existingAddress(30L, 7L, true);

		userService.updateUserAddress(activeFlagUpdate(30L, false));

		assertThat(address.isActiveFlag()).isFalse();
		verify(userAddressRepository, never()).saveAndFlush(any());
	}

	@Test
	void updateUnknownAddressIsRejected() {
		when(userAddressRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.updateUserAddress(activeFlagUpdate(99L, true)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User address does not exist.");
	}

	@Test
	void addressUpdateRequestRejectsUserId() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			assertThat(validator.validate(new NexHomeUserAddressUpdateRequest(30L, 8L, null, null, null, null, null, null,
					null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactly("User cannot be updated");
			assertThat(validator.validate(activeFlagUpdate(30L, true))).isEmpty();
		}
	}

	// ---- deleteUserAddress ----

	@Test
	void deleteUserAddressRemovesOnlyThatAddress() {
		when(userAddressRepository.existsById(30L)).thenReturn(true);

		userService.deleteUserAddress(30L);

		verify(userAddressRepository).deleteById(30L);
		// No other address is activated in its place
		verify(userAddressRepository, never()).findByUserUserIdAndActiveFlag(any(), anyBoolean());
	}

	@Test
	void deleteUnknownAddressIsRejected() {
		when(userAddressRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> userService.deleteUserAddress(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User address does not exist.");
		verify(userAddressRepository, never()).deleteById(any());
	}

	private NexHomeUser savedUser() {
		ArgumentCaptor<NexHomeUser> saved = ArgumentCaptor.forClass(NexHomeUser.class);
		verify(userRepository).save(saved.capture());
		return saved.getValue();
	}

	private void givenRole(Long roleId, String roleName, boolean active) {
		NexHomeRole role = new NexHomeRole();
		role.setRoleId(roleId);
		role.setRoleName(roleName);
		role.setActiveFlag(active);
		// The query only returns active roles, so an inactive role is "not found"
		when(roleRepository.findByRoleIdAndActiveFlag(roleId, true)).thenReturn(active ? Optional.of(role) : Optional.empty());
	}

	private NexHomeUser givenUser(Long userId) {
		NexHomeUser user = new NexHomeUser();
		user.setUserId(userId);
		when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
		return user;
	}

	private static NexHomeUserCreateRequest request(String phone, String email, Long roleId) {
		return new NexHomeUserCreateRequest(phone, email, roleId, null);
	}

	private static NexHomeUserDetailsRequest detailsRequest(Long userId, String firstName) {
		return new NexHomeUserDetailsRequest(userId, firstName, null);
	}

	private NexHomeUserAddress savedAddress() {
		ArgumentCaptor<NexHomeUserAddress> saved = ArgumentCaptor.forClass(NexHomeUserAddress.class);
		verify(userAddressRepository).save(saved.capture());
		return saved.getValue();
	}

	private static NexHomeUserAddressRequest addressRequest(Long userId, String address1, Boolean activeFlag) {
		return new NexHomeUserAddressRequest(userId, address1, "MG Road", null, "Hyderabad", "Hyderabad", "Telangana",
				"500001", activeFlag);
	}

	private NexHomeUser existingUser(Long userId, String phone, String email, Long roleId, String roleName) {
		NexHomeRole role = new NexHomeRole();
		role.setRoleId(roleId);
		role.setRoleName(roleName);
		NexHomeUser user = new NexHomeUser();
		user.setUserId(userId);
		user.setPhone(phone);
		user.setEmail(email);
		user.setRole(role);
		user.setUserStatus(NexHomeUserStatus.ACTIVATED);
		user.setActiveFlag(true);
		when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
		return user;
	}

	private NexHomeUser existingUserWithStatus(Long userId, NexHomeUserStatus status) {
		NexHomeUser user = existingUser(userId, "9876543210", null, 5L, "CUSTOMER");
		user.setUserStatus(status);
		user.setActiveFlag(false);
		return user;
	}

	private NexHomeUserDetails existingDetails(Long userId, String firstName, String lastName) {
		NexHomeUser user = new NexHomeUser();
		user.setUserId(userId);
		NexHomeUserDetails details = new NexHomeUserDetails();
		details.setUser(user);
		details.setFirstName(firstName);
		details.setLastName(lastName);
		details.setUserDetailsId(50L);
		when(userDetailsRepository.findById(50L)).thenReturn(Optional.of(details));
		return details;
	}

	private NexHomeUserAddress existingAddress(Long addressId, Long userId, boolean active) {
		NexHomeUser user = new NexHomeUser();
		user.setUserId(userId);
		NexHomeUserAddress address = new NexHomeUserAddress();
		address.setUserAddressId(addressId);
		address.setUser(user);
		address.setAddress1("Flat 4");
		address.setAddress2("MG Road");
		address.setCity("Hyderabad");
		address.setDistrict("Hyderabad");
		address.setState("Telangana");
		address.setPinCode("500001");
		address.setActiveFlag(active);
		when(userAddressRepository.findById(addressId)).thenReturn(Optional.of(address));
		return address;
	}

	private static NexHomeUserAddressUpdateRequest activeFlagUpdate(Long addressId, Boolean activeFlag) {
		return new NexHomeUserAddressUpdateRequest(addressId, null, null, null, null, null, null, null, null, activeFlag);
	}

	private static NexHomeUserUpdateRequest userUpdate(Long userId, String email, Boolean activeFlag) {
		return new NexHomeUserUpdateRequest(userId, null, null, email, activeFlag, null);
	}
}
