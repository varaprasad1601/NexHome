package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.nexhome.app.dto.NexHomeFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeFunctionResponse;
import com.nexhome.app.dto.NexHomeFunctionUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
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
	private NexHomeFunctionServiceImpl functionService;

	@BeforeEach
	void setUp() {
		functionRepository = mock(NexHomeFunctionRepository.class);
		roleFunctionRepository = mock(NexHomeRoleFunctionRepository.class);
		when(functionRepository.findByFunctionCode(any())).thenReturn(Optional.empty());
		when(functionRepository.findByFunctionName(any())).thenReturn(Optional.empty());
		when(functionRepository.save(any())).thenAnswer(invocation -> {
			NexHomeFunction function = invocation.getArgument(0);
			function.setFunctionId(8L);
			return function;
		});
		functionService = new NexHomeFunctionServiceImpl(functionRepository, roleFunctionRepository,
				new NexHomeFunctionMapper());
	}

	@Test
	void createsFunctionActiveByDefault() {
		NexHomeFunctionResponse response = functionService.createFunction(
				new NexHomeFunctionCreateRequest(" PARTNER ", " PARTNER_VERIFY ", " Partner Verification ", null));

		assertThat(response.functionId()).isEqualTo(8L);
		assertThat(response.functionModule()).isEqualTo("PARTNER");
		assertThat(response.functionCode()).isEqualTo("PARTNER_VERIFY");
		assertThat(response.functionName()).isEqualTo("Partner Verification");
		assertThat(response.activeFlag()).isTrue();
	}

	@Test
	void duplicateCodeOrNameIsRejected() {
		when(functionRepository.findByFunctionCode("PARTNER_VERIFY")).thenReturn(Optional.of(new NexHomeFunction()));
		assertThatThrownBy(() -> functionService.createFunction(
				new NexHomeFunctionCreateRequest("PARTNER", "PARTNER_VERIFY", "Partner Verification", null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Function code already exists");

		when(functionRepository.findByFunctionName("Partner Earnings")).thenReturn(Optional.of(new NexHomeFunction()));
		assertThatThrownBy(() -> functionService.createFunction(
				new NexHomeFunctionCreateRequest("PARTNER", "PARTNER_EARNINGS", "Partner Earnings", null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Function name already exists");
		verify(functionRepository, never()).save(any());
	}

	@Test
	void updateFunctionKeepsMissingFields() {
		NexHomeFunction function = existingFunction(8L, "PARTNER_VERIFY", "Partner Verification");

		functionService.updateFunction(new NexHomeFunctionUpdateRequest(8L, null, null, "Verify Partners", false));

		assertThat(function.getFunctionCode()).isEqualTo("PARTNER_VERIFY");
		assertThat(function.getFunctionName()).isEqualTo("Verify Partners");
		assertThat(function.isActiveFlag()).isFalse();
		assertThat(function.getLastUpdateBy()).isEqualTo(1L);
	}

	@Test
	void updateRequestRejectsModuleAndCode() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			assertThat(validator.validate(new NexHomeFunctionUpdateRequest(8L, "OTHER", "OTHER_CODE", null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Function module cannot be updated", "Function code cannot be updated");
			assertThat(validator.validate(new NexHomeFunctionUpdateRequest(8L, null, null, "New name", true))).isEmpty();
		}
	}

	@Test
	void deleteFunctionRemovesRoleFunctionsFirst() {
		existingFunction(8L, "PARTNER_VERIFY", "Partner Verification");

		functionService.deleteFunction(8L);

		InOrder order = inOrder(roleFunctionRepository, functionRepository);
		order.verify(roleFunctionRepository).deleteAllByFunctionId(8L);
		order.verify(functionRepository).deleteById(8L);
	}

	@Test
	void deleteUnknownFunctionIsRejected() {
		when(functionRepository.findByFunctionId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> functionService.deleteFunction(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Function does not exist.");
		verify(roleFunctionRepository, never()).deleteAllByFunctionId(any());
	}

	private NexHomeFunction existingFunction(Long functionId, String code, String name) {
		NexHomeFunction function = new NexHomeFunction();
		function.setFunctionId(functionId);
		function.setFunctionModule("PARTNER");
		function.setFunctionCode(code);
		function.setFunctionName(name);
		when(functionRepository.findByFunctionId(functionId)).thenReturn(Optional.of(function));
		return function;
	}
}
