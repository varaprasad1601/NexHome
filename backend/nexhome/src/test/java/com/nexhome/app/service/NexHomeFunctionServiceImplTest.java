package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nexhome.app.dto.NexHomeFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeFunctionResponse;
import com.nexhome.app.dto.NexHomeFunctionUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeFunctionMapper;
import com.nexhome.app.model.NexHomeFunction;
import com.nexhome.app.repository.NexHomeFunctionRepository;
import com.nexhome.app.repository.NexHomeRoleFunctionRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class NexHomeFunctionServiceImplTest {

	private NexHomeFunctionRepository functionRepository;
	private NexHomeRoleFunctionRepository roleFunctionRepository;
	private NexHomeLookupService lookupService;
	private NexHomeFunctionServiceImpl functionService;

	@BeforeEach
	void setUp() {
		functionRepository = mock(NexHomeFunctionRepository.class);
		roleFunctionRepository = mock(NexHomeRoleFunctionRepository.class);
		lookupService = mock(NexHomeLookupService.class);
		when(functionRepository.findByFunctionCode(any())).thenReturn(Optional.empty());
		when(functionRepository.save(any())).thenAnswer(invocation -> {
			NexHomeFunction function = invocation.getArgument(0);
			function.setId(8L);
			return function;
		});
		// Valid lookup codes come back trimmed, like the real service
		when(lookupService.validateCode(eq("FUNCTION_TYPE"), any(), eq("Function type")))
				.thenAnswer(invocation -> invocation.<String>getArgument(1).trim());
		NexHomePermissionService permissionService = mock(NexHomePermissionService.class);
		when(permissionService.currentUserId()).thenReturn(1L);
		functionService = new NexHomeFunctionServiceImpl(permissionService, mock(NexHomeAuditRecorder.class), functionRepository,
				roleFunctionRepository, lookupService, new NexHomeFunctionMapper());
	}

	@Test
	void createsFunctionActiveByDefault() {
		NexHomeFunctionResponse response = functionService.createFunction(new NexHomeFunctionCreateRequest(
				" PARTNER ", " PARTNER_VERIFY ", " Partner Verification ", " SCREEN ", " Verify partners ", null));

		assertThat(response.functionId()).isEqualTo(8L);
		assertThat(response.functionModule()).isEqualTo("PARTNER");
		assertThat(response.functionCode()).isEqualTo("PARTNER_VERIFY");
		assertThat(response.functionName()).isEqualTo("Partner Verification");
		assertThat(response.functionType()).isEqualTo("SCREEN");
		assertThat(response.description()).isEqualTo("Verify partners");
		assertThat(response.activeFlag()).isTrue();
	}

	@Test
	void duplicateCodeIsRejected() {
		when(functionRepository.findByFunctionCode("PARTNER_VERIFY")).thenReturn(Optional.of(new NexHomeFunction()));

		assertThatThrownBy(() -> functionService.createFunction(new NexHomeFunctionCreateRequest(
				"PARTNER", "PARTNER_VERIFY", "Partner Verification", "SCREEN", null, null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Function code already exists");
		verify(functionRepository, never()).save(any());
	}

	@Test
	void unknownFunctionTypeIsRejected() {
		when(lookupService.validateCode("FUNCTION_TYPE", "PAGE", "Function type"))
				.thenThrow(new InvalidRequestException("Function type must be one of: MODULE, SCREEN."));

		assertThatThrownBy(() -> functionService.createFunction(new NexHomeFunctionCreateRequest(
				"PARTNER", "PARTNER_VERIFY", "Partner Verification", "PAGE", null, null)))
				.isInstanceOf(InvalidRequestException.class);
		verify(functionRepository, never()).save(any());
	}

	@Test
	void updateFunctionKeepsMissingFieldsAndReplacesDescription() {
		NexHomeFunction function = existingFunction(8L);
		function.setDescription("Old description");

		functionService.updateFunction(
				new NexHomeFunctionUpdateRequest(8L, null, null, "Verify Partners", null, null, false));

		assertThat(function.getFunctionCode()).isEqualTo("PARTNER_VERIFY");
		assertThat(function.getFunctionName()).isEqualTo("Verify Partners");
		assertThat(function.getFunctionType()).isEqualTo("SCREEN");
		assertThat(function.getDescription()).isNull();
		assertThat(function.isActiveFlag()).isFalse();
		assertThat(function.getUpdatedBy()).isEqualTo(1L);
		verify(lookupService, never()).validateCode(any(), any(), any());
	}

	@Test
	void updateRequestRejectsModuleAndCode() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			assertThat(validator.validate(
					new NexHomeFunctionUpdateRequest(8L, "OTHER", "OTHER_CODE", null, null, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Function module cannot be updated", "Function code cannot be updated");
			assertThat(validator.validate(
					new NexHomeFunctionUpdateRequest(8L, null, null, "New name", "API", null, true))).isEmpty();
		}
	}

	@Test
	void createRequestRequiresUpperCaseCodes() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(new NexHomeFunctionCreateRequest(
					"partner", "partner-verify", "Partner Verification", "SCREEN", null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Function module must contain only capital letters, digits and underscores, "
									+ "starting with a letter",
							"Function code must contain only capital letters, digits and underscores, "
									+ "starting with a letter");
		}
	}

	@Test
	void deleteUnusedFunction() {
		existingFunction(8L);

		functionService.deleteFunction(8L);

		verify(functionRepository).deleteById(8L);
	}

	@Test
	void functionGrantedToRolesCannotBeDeleted() {
		existingFunction(8L);
		when(roleFunctionRepository.existsByFunctionId(8L)).thenReturn(true);

		assertThatThrownBy(() -> functionService.deleteFunction(8L))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Function is assigned to roles; deactivate it instead.");
		verify(functionRepository, never()).deleteById(any());
	}

	@Test
	void deleteUnknownFunctionIsRejected() {
		assertThatThrownBy(() -> functionService.deleteFunction(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Function does not exist.");
		verify(functionRepository, never()).deleteById(any());
	}

	private NexHomeFunction existingFunction(Long functionId) {
		NexHomeFunction function = new NexHomeFunction();
		function.setId(functionId);
		function.setFunctionModule("PARTNER");
		function.setFunctionCode("PARTNER_VERIFY");
		function.setFunctionName("Partner Verification");
		function.setFunctionType("SCREEN");
		when(functionRepository.findById(functionId)).thenReturn(Optional.of(function));
		when(functionRepository.existsById(functionId)).thenReturn(true);
		return function;
	}
}
