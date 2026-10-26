package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.nexhome.app.dto.NexHomeAddressCreateRequest;
import com.nexhome.app.dto.NexHomeAddressResponse;
import com.nexhome.app.dto.NexHomeAddressUpdateRequest;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeAddressMapper;
import com.nexhome.app.model.NexHomeAddress;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAddressRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class NexHomeAddressServiceImplTest {

	private static final Long ACTING_USER_ID = 1L;
	private static final Long OWNER_ID = 10L;

	private NexHomeAddressRepository addressRepository;
	private NexHomeUserRepository userRepository;
	private NexHomeUserRoleRepository userRoleRepository;
	private NexHomeLookupService lookupService;
	private NexHomePermissionService permissionService;
	private NexHomeAddressServiceImpl addressService;
	private NexHomeUser owner;

	@BeforeEach
	void setUp() {
		addressRepository = mock(NexHomeAddressRepository.class);
		userRepository = mock(NexHomeUserRepository.class);
		userRoleRepository = mock(NexHomeUserRoleRepository.class);
		lookupService = mock(NexHomeLookupService.class);
		permissionService = mock(NexHomePermissionService.class);
		owner = new NexHomeUser();
		owner.setId(OWNER_ID);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(OWNER_ID, "CUSTOMER"))
				.thenReturn(true);
		when(addressRepository.findByUserIdAndDefaultAddressTrue(any())).thenReturn(Optional.empty());
		when(addressRepository.save(any())).thenAnswer(invocation -> {
			NexHomeAddress address = invocation.getArgument(0);
			address.setId(70L);
			return address;
		});
		when(lookupService.validateCode(eq("ADDRESS_TYPE"), any(), eq("Address type")))
				.thenAnswer(invocation -> invocation.<String>getArgument(1).trim());
		// The caller is user 1 unless a test says otherwise
		when(permissionService.currentUserId()).thenReturn(ACTING_USER_ID);
		when(permissionService.isSelfOrCan(any(), any(), any())).thenReturn(true);
		addressService = new NexHomeAddressServiceImpl(addressRepository, userRepository, userRoleRepository,
				lookupService, permissionService, new NexHomeAddressMapper());
	}

	@Test
	void firstAddressBecomesTheDefault() {
		NexHomeAddressResponse response = addressService.createAddress(create(null, "500081", null));

		assertThat(response.isDefault()).isTrue();
		assertThat(response.addressType()).isEqualTo("HOME");
		assertThat(response.countryCode()).isEqualTo("IN");
		verify(addressRepository, never()).saveAndFlush(any());
	}

	@Test
	void firstAddressCanBeCreatedAsNotDefault() {
		assertThat(addressService.createAddress(create(false, "500081", null)).isDefault()).isFalse();
	}

	@Test
	void laterAddressIsNotDefaultUnlessAsked() {
		NexHomeAddress current = existingAddress(71L, true);
		when(addressRepository.findByUserIdAndDefaultAddressTrue(OWNER_ID)).thenReturn(Optional.of(current));

		assertThat(addressService.createAddress(create(null, "500081", null)).isDefault()).isFalse();

		assertThat(current.isDefaultAddress()).isTrue();
		verify(addressRepository, never()).saveAndFlush(any());
	}

	@Test
	void newDefaultClearsTheCurrentOneFirst() {
		NexHomeAddress current = existingAddress(71L, true);
		when(addressRepository.findByUserIdAndDefaultAddressTrue(OWNER_ID)).thenReturn(Optional.of(current));

		assertThat(addressService.createAddress(create(true, "500081", null)).isDefault()).isTrue();

		assertThat(current.isDefaultAddress()).isFalse();
		assertThat(current.getUpdatedBy()).isEqualTo(ACTING_USER_ID);
		InOrder order = inOrder(addressRepository);
		order.verify(addressRepository).saveAndFlush(current);
		order.verify(addressRepository).save(any());
	}

	@Test
	void addressesAreOnlyForCustomers() {
		when(userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(OWNER_ID, "CUSTOMER"))
				.thenReturn(false);

		assertThatThrownBy(() -> addressService.createAddress(create(null, "500081", null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Addresses are only for accounts with the CUSTOMER role.");
		verify(addressRepository, never()).save(any());

		when(userRepository.findById(99L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> addressService.createAddress(new NexHomeAddressCreateRequest(99L, "Ravi",
				"9876543210", "HOME", null, "Plot 12", null, null, "Hyderabad", "Telangana", "500081", null, null,
				null, null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not exist.");
	}

	@Test
	void unknownAddressTypeIsRejected() {
		when(lookupService.validateCode("ADDRESS_TYPE", "CASTLE", "Address type"))
				.thenThrow(new InvalidRequestException("Address type must be one of: HOME, WORK, REGISTERED, OTHER."));

		assertThatThrownBy(() -> addressService.createAddress(new NexHomeAddressCreateRequest(OWNER_ID, "Ravi",
				"9876543210", "CASTLE", null, "Plot 12", null, null, "Hyderabad", "Telangana", "500081", null, null,
				null, null)))
				.isInstanceOf(InvalidRequestException.class);
		verify(addressRepository, never()).save(any());
	}

	@Test
	void postalCodeIsCheckedForTheCountry() {
		assertThatThrownBy(() -> addressService.createAddress(create(null, "012345", null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Postal code must be a 6-digit PIN code for India.");

		NexHomeAddressResponse british = addressService.createAddress(create(null, " sw1a 1aa ", "gb"));
		assertThat(british.countryCode()).isEqualTo("GB");
		assertThat(british.postalCode()).isEqualTo("SW1A 1AA");
		verify(addressRepository).save(any());
	}

	@Test
	void coordinatesAreRoundedAndOnlyGivenAsAPair() {
		NexHomeAddressResponse response = addressService.createAddress(new NexHomeAddressCreateRequest(OWNER_ID,
				"Ravi", "9876543210", "HOME", null, "Plot 12", null, null, "Hyderabad", "Telangana", "500081", null,
				new BigDecimal("17.385044123"), new BigDecimal("78.48667189"), null));
		assertThat(response.latitude()).isEqualByComparingTo("17.3850441");
		assertThat(response.longitude()).isEqualByComparingTo("78.4866719");

		existingAddress(71L, false);
		assertThatThrownBy(() -> addressService.updateAddress(update(71L, null, null, null, BigDecimal.ONE)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Latitude and longitude must be given together.");
	}

	@Test
	void updateToDefaultClearsTheCurrentDefault() {
		NexHomeAddress current = existingAddress(71L, true);
		NexHomeAddress other = existingAddress(72L, false);
		when(addressRepository.findByUserIdAndDefaultAddressTrue(OWNER_ID)).thenReturn(Optional.of(current));

		assertThat(addressService.updateAddress(update(72L, true, null, null, null)).isDefault()).isTrue();

		assertThat(other.isDefaultAddress()).isTrue();
		assertThat(current.isDefaultAddress()).isFalse();
		verify(addressRepository).saveAndFlush(current);
	}

	@Test
	void updateOfTheDefaultKeepsItWithoutClearing() {
		NexHomeAddress current = existingAddress(71L, true);
		when(addressRepository.findByUserIdAndDefaultAddressTrue(OWNER_ID)).thenReturn(Optional.of(current));

		assertThat(addressService.updateAddress(update(71L, true, null, null, null)).isDefault()).isTrue();
		verify(addressRepository, never()).saveAndFlush(any());

		// Missing isDefault keeps the flag; false clears it
		assertThat(addressService.updateAddress(update(71L, null, null, null, null)).isDefault()).isTrue();
		assertThat(addressService.updateAddress(update(71L, false, null, null, null)).isDefault()).isFalse();
		verify(addressRepository, never()).saveAndFlush(any());
	}

	@Test
	void changingCountryToIndiaChecksTheExistingPostalCode() {
		NexHomeAddress address = existingAddress(71L, false);
		address.setCountryCode("US");
		address.setPostalCode("94105");

		assertThatThrownBy(() -> addressService.updateAddress(update(71L, null, null, "in", null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Postal code must be a 6-digit PIN code for India.");
	}

	@Test
	void unknownAddressTypeOnUpdateLeavesAddressUnchanged() {
		NexHomeAddress address = existingAddress(71L, false);
		when(lookupService.validateCode("ADDRESS_TYPE", "CASTLE", "Address type"))
				.thenThrow(new InvalidRequestException("Address type must be one of: HOME, WORK, REGISTERED, OTHER."));

		assertThatThrownBy(() -> addressService.updateAddress(new NexHomeAddressUpdateRequest(71L, null, null,
				"Someone Else", null, "CASTLE", null, null, null, null, null, null, null, null, null, null, null)))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(address.getContactName()).isEqualTo("Ravi");
		assertThat(address.getAddressType()).isEqualTo("HOME");
	}

	@Test
	void someoneElsesAddressNeedsProfileManage() {
		NexHomeAddress address = existingAddress(71L, false);
		when(permissionService.isSelfOrCan(OWNER_ID, "PROFILE_MANAGE", "WRITE")).thenReturn(false);
		when(permissionService.isSelfOrCan(OWNER_ID, "PROFILE_MANAGE", "DELETE")).thenReturn(false);

		assertThatThrownBy(() -> addressService.updateAddress(update(71L, true, "Office", null, null)))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(address.getLabel()).isEqualTo("Home");
		assertThat(address.isDefaultAddress()).isFalse();
		assertThatThrownBy(() -> addressService.deleteAddress(71L)).isInstanceOf(AccessDeniedException.class);
		verify(addressRepository, never()).delete(any());
	}

	@Test
	void addressesOfMissingUserAreNotFound() {
		when(userRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> addressService.getUserAddresses(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not exist.");
		assertThatThrownBy(() -> addressService.getAddress(404L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Address does not exist.");
	}

	@Test
	void requestsValidatePostalAndCountryCodesAndRejectOwnerChanges() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(new NexHomeAddressCreateRequest(OWNER_ID, "Ravi", "98765",
					"HOME", null, "Plot 12", null, null, "Hyderabad", "Telangana", "5#", "IND",
					new BigDecimal("91"), new BigDecimal("-181"), null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Mobile number must be 6 to 15 digits",
							"Postal code must be 3 to 12 letters, digits, spaces or hyphens",
							"Country code must be a 2-letter ISO country code, e.g. IN",
							"Latitude must be between -90 and 90",
							"Longitude must be between -180 and 180");
			assertThat(factory.getValidator().validate(new NexHomeAddressUpdateRequest(71L, 2L, "NHADDR0001", " ",
					null, null, null, null, null, null, null, null, null, null, null, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("User cannot be updated", "Public ID cannot be updated",
							"Contact name cannot be blank");
		}
	}

	private static NexHomeAddressCreateRequest create(Boolean isDefault, String postalCode, String countryCode) {
		return new NexHomeAddressCreateRequest(OWNER_ID, "Ravi", "9876543210", " HOME ", "Home", "Plot 12, Road 3",
				null, null, "Hyderabad", "Telangana", postalCode, countryCode, null, null, isDefault);
	}

	private static NexHomeAddressUpdateRequest update(Long addressId, Boolean isDefault, String label,
			String countryCode, BigDecimal latitude) {
		return new NexHomeAddressUpdateRequest(addressId, null, null, null, null, null, label, null, null, null,
				null, null, null, countryCode, latitude, null, isDefault);
	}

	private NexHomeAddress existingAddress(Long id, boolean isDefault) {
		NexHomeAddress address = new NexHomeAddressMapper().toAddressEntity(create(null, "500081", null), owner,
				OWNER_ID);
		address.setId(id);
		address.setAddressType("HOME");
		address.setDefaultAddress(isDefault);
		when(addressRepository.findById(id)).thenReturn(Optional.of(address));
		return address;
	}
}
