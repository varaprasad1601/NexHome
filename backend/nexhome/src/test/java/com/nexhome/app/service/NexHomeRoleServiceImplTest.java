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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.nexhome.app.dto.NexHomeRoleCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionCreateRequest;
import com.nexhome.app.dto.NexHomeRoleFunctionResponse;
import com.nexhome.app.dto.NexHomeRoleFunctionUpdateRequest;
import com.nexhome.app.dto.NexHomeRoleResponse;
import com.nexhome.app.dto.NexHomeRoleUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeRoleFunctionMapper;
import com.nexhome.app.mapper.NexHomeRoleMapper;
import com.nexhome.app.model.NexHomeFunction;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeRoleFunction;
import com.nexhome.app.repository.NexHomeFunctionRepository;
import com.nexhome.app.repository.NexHomeRoleFunctionRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserAddressRepository;
import com.nexhome.app.repository.NexHomeUserDetailsRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class NexHomeRoleServiceImplTest {

	private NexHomeRoleRepository roleRepository;
	private NexHomeFunctionRepository functionRepository;
	private NexHomeRoleFunctionRepository roleFunctionRepository;
	private NexHomeUserRepository userRepository;
	private NexHomeUserDetailsRepository userDetailsRepository;
	private NexHomeUserAddressRepository userAddressRepository;
	private NexHomeRoleServiceImpl roleService;

	@BeforeEach
	void setUp() {
		roleRepository = mock(NexHomeRoleRepository.class);
		functionRepository = mock(NexHomeFunctionRepository.class);
		roleFunctionRepository = mock(NexHomeRoleFunctionRepository.class);
		userRepository = mock(NexHomeUserRepository.class);
		userDetailsRepository = mock(NexHomeUserDetailsRepository.class);
		userAddressRepository = mock(NexHomeUserAddressRepository.class);
		when(roleRepository.findByRoleName(any())).thenReturn(Optional.empty());
		when(roleRepository.save(any())).thenAnswer(invocation -> {
			NexHomeRole role = invocation.getArgument(0);
			role.setRoleId(5L);
			return role;
		});
		when(roleFunctionRepository.findByRoleRoleIdAndFunctionFunctionId(any(), any())).thenReturn(Optional.empty());
		when(roleFunctionRepository.save(any())).thenAnswer(invocation -> {
			NexHomeRoleFunction roleFunction = invocation.getArgument(0);
			roleFunction.setRoleFunctionId(40L);
			return roleFunction;
		});
		roleService = new NexHomeRoleServiceImpl(roleRepository, functionRepository, roleFunctionRepository,
				userRepository, userDetailsRepository, userAddressRepository, new NexHomeRoleMapper(),
				new NexHomeRoleFunctionMapper());
	}

	// ---- roles ----

	@Test
	void createsRoleActiveByDefault() {
		NexHomeRoleResponse response = roleService.createRole(new NexHomeRoleCreateRequest(" PARTNER ", " Partners ", null));

		ArgumentCaptor<NexHomeRole> saved = ArgumentCaptor.forClass(NexHomeRole.class);
		verify(roleRepository).save(saved.capture());
		assertThat(saved.getValue().getRoleName()).isEqualTo("PARTNER");
		assertThat(saved.getValue().getDescription()).isEqualTo("Partners");
		assertThat(saved.getValue().isActiveFlag()).isTrue();
		assertThat(saved.getValue().getCreatedBy()).isEqualTo(1L);
		assertThat(response.roleId()).isEqualTo(5L);
	}

	@Test
	void duplicateRoleNameIsRejected() {
		when(roleRepository.findByRoleName("PARTNER")).thenReturn(Optional.of(new NexHomeRole()));

		assertThatThrownBy(() -> roleService.createRole(new NexHomeRoleCreateRequest("PARTNER", null, null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Role name already exists");
		verify(roleRepository, never()).save(any());
	}

	@Test
	void updateRoleKeepsNameWhenMissingAndReplacesDescription() {
		NexHomeRole role = existingRole(5L, "PARTNER", "Partners");

		roleService.updateRole(new NexHomeRoleUpdateRequest(5L, null, null, false));

		assertThat(role.getRoleName()).isEqualTo("PARTNER");
		assertThat(role.getDescription()).isNull();
		assertThat(role.isActiveFlag()).isFalse();
		assertThat(role.getLastUpdateBy()).isEqualTo(1L);
	}

	@Test
	void roleUpdateRequestRejectsRoleName() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			assertThat(validator.validate(new NexHomeRoleUpdateRequest(5L, "CUSTOMER", null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactly("Role name cannot be updated");
			assertThat(validator.validate(new NexHomeRoleUpdateRequest(5L, null, "Partners", true))).isEmpty();
		}
	}

	@Test
	void deleteRoleRemovesChildrenBeforeTheRole() {
		existingRole(5L, "PARTNER", null);

		roleService.deleteRole(5L);

		InOrder order = inOrder(userAddressRepository, userDetailsRepository, userRepository, roleFunctionRepository,
				roleRepository);
		order.verify(userAddressRepository).deleteAllByRoleId(5L);
		order.verify(userDetailsRepository).deleteAllByRoleId(5L);
		order.verify(userRepository).deleteAllByRoleId(5L);
		order.verify(roleFunctionRepository).deleteAllByRoleId(5L);
		order.verify(roleRepository).deleteById(5L);
	}

	@Test
	void deleteUnknownRoleIsRejected() {
		when(roleRepository.findByRoleId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> roleService.deleteRole(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Role does not exist.");
		verify(userRepository, never()).deleteAllByRoleId(any());
	}

	// ---- role functions ----

	@Test
	void addRoleFunctionGrantsReadWithWrite() {
		existingRole(5L, "PARTNER", null);
		existingFunction(8L);

		NexHomeRoleFunctionResponse response = roleService.addRoleFunction(
				new NexHomeRoleFunctionCreateRequest(5L, 8L, null, true, null, null));

		assertThat(response.read()).isTrue();
		assertThat(response.write()).isTrue();
		assertThat(response.delete()).isFalse();
		assertThat(response.activeFlag()).isTrue();
		assertThat(response.roleName()).isEqualTo("PARTNER");
		assertThat(response.functionCode()).isEqualTo("FN_8");
	}

	@Test
	void addRoleFunctionRejectsUnknownRoleOrFunction() {
		when(roleRepository.findByRoleId(99L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> roleService.addRoleFunction(new NexHomeRoleFunctionCreateRequest(99L, 8L, true, null, null, null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Role does not exist.");

		existingRole(5L, "PARTNER", null);
		when(functionRepository.findByFunctionId(99L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> roleService.addRoleFunction(new NexHomeRoleFunctionCreateRequest(5L, 99L, true, null, null, null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Function does not exist.");
	}

	@Test
	void sameFunctionTwiceForRoleIsRejected() {
		existingRole(5L, "PARTNER", null);
		existingFunction(8L);
		when(roleFunctionRepository.findByRoleRoleIdAndFunctionFunctionId(5L, 8L))
				.thenReturn(Optional.of(new NexHomeRoleFunction()));

		assertThatThrownBy(() -> roleService.addRoleFunction(new NexHomeRoleFunctionCreateRequest(5L, 8L, true, null, null, null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Function is already assigned to this role");
	}

	@Test
	void updateRoleFunctionKeepsMissingFlagsAndGrantsReadWithDelete() {
		NexHomeRoleFunction roleFunction = new NexHomeRoleFunction();
		roleFunction.setRoleFunctionId(40L);
		roleFunction.setRole(existingRole(5L, "PARTNER", null));
		roleFunction.setFunction(existingFunction(8L));
		roleFunction.setWrite(true);
		when(roleFunctionRepository.findById(40L)).thenReturn(Optional.of(roleFunction));

		roleService.updateRoleFunction(new NexHomeRoleFunctionUpdateRequest(40L, null, null, false, null, true, null));

		assertThat(roleFunction.isWrite()).isTrue();
		assertThat(roleFunction.isDelete()).isTrue();
		// read was set to false, but write and delete need it
		assertThat(roleFunction.isRead()).isTrue();
		assertThat(roleFunction.getLastUpdateBy()).isEqualTo(1L);
	}

	@Test
	void roleFunctionUpdateRequestRejectsRoleAndFunction() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			assertThat(validator.validate(new NexHomeRoleFunctionUpdateRequest(40L, 6L, 9L, null, null, null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Role cannot be updated", "Function cannot be updated");
			assertThat(validator.validate(new NexHomeRoleFunctionUpdateRequest(40L, null, null, true, null, null, null)))
					.isEmpty();
		}
	}

	@Test
	void deleteUnknownRoleFunctionIsRejected() {
		when(roleFunctionRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> roleService.deleteRoleFunction(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Role function does not exist.");
		verify(roleFunctionRepository, never()).deleteById(any());
	}

	private NexHomeRole existingRole(Long roleId, String roleName, String description) {
		NexHomeRole role = new NexHomeRole();
		role.setRoleId(roleId);
		role.setRoleName(roleName);
		role.setDescription(description);
		when(roleRepository.findByRoleId(roleId)).thenReturn(Optional.of(role));
		return role;
	}

	private NexHomeFunction existingFunction(Long functionId) {
		NexHomeFunction function = new NexHomeFunction();
		function.setFunctionId(functionId);
		function.setFunctionCode("FN_" + functionId);
		function.setFunctionName("Function " + functionId);
		when(functionRepository.findByFunctionId(functionId)).thenReturn(Optional.of(function));
		return function;
	}
}
