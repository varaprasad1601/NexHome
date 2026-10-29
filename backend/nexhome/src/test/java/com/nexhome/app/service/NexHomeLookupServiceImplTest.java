package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.nexhome.app.dto.NexHomeLookupTypeCreateRequest;
import com.nexhome.app.dto.NexHomeLookupTypeResponse;
import com.nexhome.app.dto.NexHomeLookupTypeUpdateRequest;
import com.nexhome.app.dto.NexHomeLookupValueCreateRequest;
import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomeLookupValueUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeLookupTypeMapper;
import com.nexhome.app.mapper.NexHomeLookupValueMapper;
import com.nexhome.app.model.NexHomeLookupType;
import com.nexhome.app.model.NexHomeLookupValue;
import com.nexhome.app.repository.NexHomeLookupTypeRepository;
import com.nexhome.app.repository.NexHomeLookupValueRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.json.JsonMapper;

class NexHomeLookupServiceImplTest {

	private NexHomeLookupTypeRepository lookupTypeRepository;
	private NexHomeLookupValueRepository lookupValueRepository;
	private NexHomeLookupServiceImpl lookupService;

	@BeforeEach
	void setUp() {
		lookupTypeRepository = mock(NexHomeLookupTypeRepository.class);
		lookupValueRepository = mock(NexHomeLookupValueRepository.class);
		when(lookupTypeRepository.findByTypeCode(any())).thenReturn(Optional.empty());
		when(lookupTypeRepository.save(any())).thenAnswer(invocation -> {
			NexHomeLookupType type = invocation.getArgument(0);
			type.setId(3L);
			return type;
		});
		when(lookupValueRepository.findByLookupTypeIdAndValueCode(any(), any())).thenReturn(Optional.empty());
		when(lookupValueRepository.findByLookupTypeIdAndDefaultValueTrue(any())).thenReturn(Optional.empty());
		when(lookupValueRepository.save(any())).thenAnswer(invocation -> {
			NexHomeLookupValue value = invocation.getArgument(0);
			value.setId(40L);
			return value;
		});
		NexHomePermissionService permissionService = mock(NexHomePermissionService.class);
		when(permissionService.currentUserId()).thenReturn(1L);
		lookupService = new NexHomeLookupServiceImpl(permissionService, mock(NexHomeAuditRecorder.class),
				lookupTypeRepository, lookupValueRepository,
				new NexHomeLookupTypeMapper(), new NexHomeLookupValueMapper(JsonMapper.builder().build()));
	}

	@Test
	void createsAdminManagedTypeByDefault() {
		NexHomeLookupTypeResponse response = lookupService.createLookupType(
				new NexHomeLookupTypeCreateRequest(" SERVICE_TAG ", " Service Tag ", null, null, null));

		assertThat(response.lookupTypeId()).isEqualTo(3L);
		assertThat(response.typeCode()).isEqualTo("SERVICE_TAG");
		assertThat(response.typeName()).isEqualTo("Service Tag");
		assertThat(response.activeFlag()).isTrue();
		assertThat(response.isSystemManaged()).isFalse();
	}

	@Test
	void duplicateTypeCodeIsRejected() {
		when(lookupTypeRepository.findByTypeCode("BOOKING_STATUS")).thenReturn(Optional.of(new NexHomeLookupType()));

		assertThatThrownBy(() -> lookupService.createLookupType(
				new NexHomeLookupTypeCreateRequest("BOOKING_STATUS", "Booking Status", null, null, null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Lookup type code already exists");
	}

	@Test
	void typeCodeMustBeUpperCase() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(
					new NexHomeLookupTypeCreateRequest("booking status", "Booking Status", null, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactly("Type code must contain only capital letters, digits and underscores, "
							+ "starting with a letter");
		}
	}

	@Test
	void systemManagedTypeCannotBeDeactivated() {
		NexHomeLookupType type = existingType(true);

		assertThatThrownBy(() -> lookupService.updateLookupType(
				new NexHomeLookupTypeUpdateRequest(3L, null, null, null, false, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("A system managed lookup type cannot be deactivated.");
		assertThat(type.isActiveFlag()).isTrue();
	}

	@Test
	void adminManagedTypeCanBeDeactivated() {
		NexHomeLookupType type = existingType(false);
		type.setDescription("Old");

		lookupService.updateLookupType(new NexHomeLookupTypeUpdateRequest(3L, null, "Renamed", null, false, null));

		assertThat(type.isActiveFlag()).isFalse();
		assertThat(type.getTypeName()).isEqualTo("Renamed");
		assertThat(type.getDescription()).isNull();
	}

	@Test
	void addsValueWithMetadata() {
		existingType(false);

		NexHomeLookupValueResponse response = lookupService.addLookupValue(new NexHomeLookupValueCreateRequest(
				3L, " te ", " Telugu ", null, 2, null, null, Map.of("script", "Telugu")));

		assertThat(response.lookupValueId()).isEqualTo(40L);
		assertThat(response.typeCode()).isEqualTo("LANGUAGE");
		assertThat(response.valueCode()).isEqualTo("te");
		assertThat(response.displayName()).isEqualTo("Telugu");
		assertThat(response.displayOrder()).isEqualTo(2);
		assertThat(response.activeFlag()).isTrue();
		assertThat(response.isDefault()).isFalse();
		assertThat(response.metadata()).containsEntry("script", "Telugu");
	}

	@Test
	void duplicateValueCodeIsRejected() {
		existingType(false);
		when(lookupValueRepository.findByLookupTypeIdAndValueCode(3L, "te"))
				.thenReturn(Optional.of(new NexHomeLookupValue()));

		assertThatThrownBy(() -> lookupService.addLookupValue(
				new NexHomeLookupValueCreateRequest(3L, "te", "Telugu", null, null, null, null, null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Value code already exists for this lookup type");
	}

	@Test
	void newDefaultReplacesCurrentDefaultFirst() {
		NexHomeLookupType type = existingType(false);
		NexHomeLookupValue current = value(type, 41L, "en");
		current.setDefaultValue(true);
		when(lookupValueRepository.findByLookupTypeIdAndDefaultValueTrue(3L)).thenReturn(Optional.of(current));

		NexHomeLookupValueResponse response = lookupService.addLookupValue(
				new NexHomeLookupValueCreateRequest(3L, "te", "Telugu", null, null, null, true, null));

		assertThat(response.isDefault()).isTrue();
		assertThat(current.isDefaultValue()).isFalse();
		InOrder order = inOrder(lookupValueRepository);
		order.verify(lookupValueRepository).saveAndFlush(current);
		order.verify(lookupValueRepository).save(any());
	}

	@Test
	void inactiveValueCannotBeDefault() {
		existingType(false);

		assertThatThrownBy(() -> lookupService.addLookupValue(
				new NexHomeLookupValueCreateRequest(3L, "te", "Telugu", null, null, false, true, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("An inactive lookup value cannot be the default.");
		verify(lookupValueRepository, never()).save(any());
	}

	@Test
	void valuesOfSystemManagedTypeCannotBeDeactivated() {
		NexHomeLookupValue value = value(existingType(true), 41L, "ACTIVE");
		when(lookupValueRepository.findById(41L)).thenReturn(Optional.of(value));

		assertThatThrownBy(() -> lookupService.updateLookupValue(new NexHomeLookupValueUpdateRequest(
				41L, null, null, null, null, null, false, null, null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Values of a system managed lookup type cannot be deactivated.");
	}

	@Test
	void updateValueKeepsMissingFields() {
		NexHomeLookupValue value = value(existingType(false), 41L, "te");
		value.setDisplayOrder(5);
		value.setMetadata("{\"script\":\"Telugu\"}");
		when(lookupValueRepository.findById(41L)).thenReturn(Optional.of(value));

		lookupService.updateLookupValue(new NexHomeLookupValueUpdateRequest(
				41L, null, null, "Telugu (te)", null, null, null, null, null));

		assertThat(value.getDisplayName()).isEqualTo("Telugu (te)");
		assertThat(value.getDisplayOrder()).isEqualTo(5);
		assertThat(value.getMetadata()).isEqualTo("{\"script\":\"Telugu\"}");
		assertThat(value.isActiveFlag()).isTrue();
	}

	@Test
	void validateCodeAcceptsActiveCodesAndListsThemOtherwise() {
		when(lookupValueRepository.existsByLookupTypeTypeCodeAndLookupTypeActiveFlagTrueAndValueCodeAndActiveFlagTrue(
				"ACCOUNT_STATUS", "ACTIVE")).thenReturn(true);
		NexHomeLookupType type = existingType(true);
		when(lookupValueRepository.findByLookupTypeTypeCodeAndActiveFlagTrueOrderByDisplayOrderAscDisplayNameAsc(
				"ACCOUNT_STATUS")).thenReturn(List.of(value(type, 1L, "ACTIVE"), value(type, 2L, "BLOCKED")));

		assertThat(lookupService.validateCode("ACCOUNT_STATUS", " ACTIVE ", "Account status")).isEqualTo("ACTIVE");
		assertThatThrownBy(() -> lookupService.validateCode("ACCOUNT_STATUS", "ARCHIVED", "Account status"))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Account status must be one of: ACTIVE, BLOCKED.");
	}

	@Test
	void inactiveTypeHasNoDropdownValues() {
		NexHomeLookupType type = existingType(false);
		type.setActiveFlag(false);
		when(lookupTypeRepository.findByTypeCode("LANGUAGE")).thenReturn(Optional.of(type));

		assertThatThrownBy(() -> lookupService.getActiveLookupValues("LANGUAGE"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Lookup type does not exist or is inactive.");
	}

	private NexHomeLookupType existingType(boolean systemManaged) {
		NexHomeLookupType type = new NexHomeLookupType();
		type.setId(3L);
		type.setTypeCode(systemManaged ? "ACCOUNT_STATUS" : "LANGUAGE");
		type.setTypeName(systemManaged ? "Account Status" : "Language");
		type.setSystemManaged(systemManaged);
		when(lookupTypeRepository.findById(3L)).thenReturn(Optional.of(type));
		return type;
	}

	private static NexHomeLookupValue value(NexHomeLookupType type, Long id, String code) {
		NexHomeLookupValue value = new NexHomeLookupValue();
		value.setId(id);
		value.setLookupType(type);
		value.setValueCode(code);
		value.setDisplayName(code);
		return value;
	}
}
