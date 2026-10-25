package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class NexHomeRoleServiceImplTest {

	private static final Short ROLE_ID = 4;

	private NexHomeRoleRepository roleRepository;
	private NexHomeFunctionRepository functionRepository;
	private NexHomeRoleFunctionRepository roleFunctionRepository;
	private NexHomeUserRoleRepository userRoleRepository;
	private NexHomeRoleServiceImpl roleService;

	@BeforeEach
	void setUp() {
		roleRepository = mock(NexHomeRoleRepository.class);
		functionRepository = mock(NexHomeFunctionRepository.class);
		roleFunctionRepository = mock(NexHomeRoleFunctionRepository.class);
		userRoleRepository = mock(NexHomeUserRoleRepository.class);
		when(roleRepository.findByCode(any())).thenReturn(Optional.empty());
		when(roleRepository.save(any())).thenAnswer(invocation -> {
			NexHomeRole role = invocation.getArgument(0);
			role.setId(ROLE_ID);
			return role;
		});
		when(roleFunctionRepository.findByRoleIdAndFunctionId(any(), any())).thenReturn(Optional.empty());
		when(roleFunctionRepository.save(any())).thenAnswer(invocation -> {
			NexHomeRoleFunction roleFunction = invocation.getArgument(0);
			roleFunction.setId(30L);
			return roleFunction;
		});
		NexHomePermissionService permissionService = mock(NexHomePermissionService.class);
		when(permissionService.currentUserId()).thenReturn(1L);
		roleService = new NexHomeRoleServiceImpl(permissionService, roleRepository, functionRepository,
				roleFunctionRepository, userRoleRepository, new NexHomeRoleMapper(), new NexHomeRoleFunctionMapper());
	}

	@Test
	void createsRoleActiveByDefault() {
		NexHomeRoleResponse response = roleService.createRole(
				new NexHomeRoleCreateRequest(" CUSTOMER ", " Customer ", "  ", null));

		assertThat(response.roleId()).isEqualTo(ROLE_ID);
		assertThat(response.code()).isEqualTo("CUSTOMER");
		assertThat(response.name()).isEqualTo("Customer");
		assertThat(response.description()).isNull();
		assertThat(response.activeFlag()).isTrue();
	}

	@Test
	void duplicateRoleCodeIsRejected() {
		when(roleRepository.findByCode("CUSTOMER")).thenReturn(Optional.of(new NexHomeRole()));

		assertThatThrownBy(() -> roleService.createRole(new NexHomeRoleCreateRequest("CUSTOMER", "Customer", null,
				null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Role code already exists");
		verify(roleRepository, never()).save(any());
	}

	@Test
	void updateRoleKeepsMissingNameAndReplacesDescription() {
		NexHomeRole role = existingRole();
		role.setDescription("Old");

		roleService.updateRole(new NexHomeRoleUpdateRequest(ROLE_ID, null, null, null, false));

		assertThat(role.getName()).isEqualTo("Customer");
		assertThat(role.getDescription()).isNull();
		assertThat(role.isActiveFlag()).isFalse();
		assertThat(role.getUpdatedBy()).isEqualTo(1L);
	}

	@Test
	void updateRequestRejectsCode() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();

			assertThat(validator.validate(new NexHomeRoleUpdateRequest(ROLE_ID, "OTHER", " ", null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Role code cannot be updated", "Role name cannot be blank");
		}
	}

	@Test
	void deleteUnusedRole() {
		existingRole();

		roleService.deleteRole(ROLE_ID);

		verify(roleRepository).deleteById(ROLE_ID);
	}

	@Test
	void roleInUseCannotBeDeleted() {
		existingRole();
		when(userRoleRepository.existsByRoleId(ROLE_ID)).thenReturn(true);

		assertThatThrownBy(() -> roleService.deleteRole(ROLE_ID))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Role is assigned to users or functions; deactivate it instead.");

		when(userRoleRepository.existsByRoleId(ROLE_ID)).thenReturn(false);
		when(roleFunctionRepository.existsByRoleId(ROLE_ID)).thenReturn(true);
		assertThatThrownBy(() -> roleService.deleteRole(ROLE_ID))
				.isInstanceOf(DuplicateResourceException.class);
		verify(roleRepository, never()).deleteById(any());
	}

	@Test
	void writeOrDeleteAlsoGrantsRead() {
		existingRole();
		existingFunction();

		NexHomeRoleFunctionResponse response = roleService.addRoleFunction(
				new NexHomeRoleFunctionCreateRequest(ROLE_ID, 8L, null, true, null, null));

		assertThat(response.roleFunctionId()).isEqualTo(30L);
		assertThat(response.roleCode()).isEqualTo("CUSTOMER");
		assertThat(response.functionCode()).isEqualTo("PARTNER_VERIFY");
		assertThat(response.canRead()).isTrue();
		assertThat(response.canWrite()).isTrue();
		assertThat(response.canDelete()).isFalse();
		assertThat(response.activeFlag()).isTrue();
	}

	@Test
	void duplicateRoleFunctionIsRejected() {
		existingRole();
		existingFunction();
		when(roleFunctionRepository.findByRoleIdAndFunctionId(ROLE_ID, 8L))
				.thenReturn(Optional.of(new NexHomeRoleFunction()));

		assertThatThrownBy(() -> roleService.addRoleFunction(
				new NexHomeRoleFunctionCreateRequest(ROLE_ID, 8L, true, null, null, null)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("Function is already assigned to this role");
	}

	@Test
	void roleFunctionNeedsExistingRoleAndFunction() {
		assertThatThrownBy(() -> roleService.addRoleFunction(
				new NexHomeRoleFunctionCreateRequest(ROLE_ID, 8L, true, null, null, null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Role does not exist.");

		existingRole();
		assertThatThrownBy(() -> roleService.addRoleFunction(
				new NexHomeRoleFunctionCreateRequest(ROLE_ID, 8L, true, null, null, null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Function does not exist.");
	}

	@Test
	void updateRoleFunctionKeepsNullFlags() {
		NexHomeRoleFunction roleFunction = new NexHomeRoleFunction();
		roleFunction.setId(30L);
		roleFunction.setRole(existingRole());
		roleFunction.setFunction(existingFunction());
		roleFunction.setCanRead(true);
		when(roleFunctionRepository.findById(30L)).thenReturn(Optional.of(roleFunction));

		roleService.updateRoleFunction(new NexHomeRoleFunctionUpdateRequest(30L, null, null, null, null, true, null));

		assertThat(roleFunction.isCanRead()).isTrue();
		assertThat(roleFunction.isCanWrite()).isFalse();
		assertThat(roleFunction.isCanDelete()).isTrue();
		assertThat(roleFunction.isActiveFlag()).isTrue();
	}

	private NexHomeRole existingRole() {
		NexHomeRole role = new NexHomeRole();
		role.setId(ROLE_ID);
		role.setCode("CUSTOMER");
		role.setName("Customer");
		when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
		when(roleRepository.existsById(ROLE_ID)).thenReturn(true);
		return role;
	}

	private NexHomeFunction existingFunction() {
		NexHomeFunction function = new NexHomeFunction();
		function.setId(8L);
		function.setFunctionCode("PARTNER_VERIFY");
		function.setFunctionName("Partner Verification");
		when(functionRepository.findById(8L)).thenReturn(Optional.of(function));
		return function;
	}
}
