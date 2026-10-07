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
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nexhome.app.dto.NexHomeUserRegisterRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

class NexHomeUserServiceImplTest {

	private static final String PASSWORD = "secret-pass-1";

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private NexHomeUserRepository userRepository;
	private NexHomeRoleRepository roleRepository;
	private NexHomeUserServiceImpl userService;

	@BeforeEach
	void setUp() {
		userRepository = mock(NexHomeUserRepository.class);
		roleRepository = mock(NexHomeRoleRepository.class);
		when(userRepository.findByPhoneAndRoleRoleId(any(), any())).thenReturn(Optional.empty());
		when(userRepository.save(any())).thenAnswer(invocation -> {
			NexHomeUser user = invocation.getArgument(0);
			user.setUserId(100L);
			return user;
		});
		userService = new NexHomeUserServiceImpl(userRepository, roleRepository, new NexHomeUserMapper(),
				passwordEncoder);
	}

	@Test
	void registersUserWithHashedPasswordAndDefaultAuditUser() {
		givenRole(5L, "CUSTOMER", true);

		NexHomeUserResponse response = userService.register(request(" 9876543210 ", " Ravi@Example.com ", 5L));

		NexHomeUser user = savedUser();
		assertThat(user.getPhone()).isEqualTo("9876543210");
		assertThat(user.getEmail()).isEqualTo("ravi@example.com");
		assertThat(user.getPassword()).isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, user.getPassword())).isTrue();
		assertThat(user.isActiveFlag()).isTrue();
		assertThat(user.getCreatedBy()).isEqualTo(1L);
		assertThat(user.getLastUpdateBy()).isEqualTo(1L);

		assertThat(response.userId()).isEqualTo(100L);
		assertThat(response.roleName()).isEqualTo("CUSTOMER");
		assertThat(response.createdBy()).isEqualTo(1L);
	}

	@Test
	void blankEmailIsStoredAsNull() {
		givenRole(5L, "CUSTOMER", true);

		userService.register(request("9876543210", "  ", 5L));

		assertThat(savedUser().getEmail()).isNull();
	}

	@Test
	void samePhoneAndRoleIsRejected() {
		when(userRepository.findByPhoneAndRoleRoleId("9876543210", 5L)).thenReturn(Optional.of(new NexHomeUser()));

		assertThatThrownBy(() -> userService.register(request(" 9876543210 ", null, 5L)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("User already exists with this phone number");
		verify(userRepository, never()).save(any());
	}

	@Test
	void samePhoneWithDifferentRoleIsAllowed() {
		when(userRepository.findByPhoneAndRoleRoleId("9876543210", 5L)).thenReturn(Optional.of(new NexHomeUser()));
		givenRole(6L, "PARTNER", true);

		NexHomeUserResponse response = userService.register(request("9876543210", null, 6L));

		assertThat(response.roleName()).isEqualTo("PARTNER");
		assertThat(savedUser().getRole().getRoleId()).isEqualTo(6L);
	}

	@Test
	void unknownRoleIsRejected() {
		when(roleRepository.findByRoleId(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.register(request("9876543210", null, 99L)))
				.isInstanceOf(InvalidRequestException.class);
		verify(userRepository, never()).save(any());
	}

	@Test
	void inactiveRoleIsRejected() {
		givenRole(5L, "CUSTOMER", false);

		assertThatThrownBy(() -> userService.register(request("9876543210", null, 5L)))
				.isInstanceOf(InvalidRequestException.class);
		verify(userRepository, never()).save(any());
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
		when(roleRepository.findByRoleId(roleId)).thenReturn(Optional.of(role));
	}

	private static NexHomeUserRegisterRequest request(String phone, String email, Long roleId) {
		return new NexHomeUserRegisterRequest(phone, email, PASSWORD, roleId);
	}
}
