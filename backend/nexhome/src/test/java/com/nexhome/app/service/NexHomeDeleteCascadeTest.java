package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.model.NexHomeFunction;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeRoleFunction;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserAddress;
import com.nexhome.app.model.NexHomeUserDetails;
import com.nexhome.app.repository.NexHomeFunctionRepository;
import com.nexhome.app.repository.NexHomeRoleFunctionRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserAddressRepository;
import com.nexhome.app.repository.NexHomeUserDetailsRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

import jakarta.persistence.EntityManager;

/**
 * Checks the cascading deletes against the dev database; every test is rolled back, so no data is kept.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class NexHomeDeleteCascadeTest {

	@Autowired
	private NexHomeUserService userService;

	@Autowired
	private NexHomeRoleService roleService;

	@Autowired
	private NexHomeFunctionService functionService;

	@Autowired
	private NexHomeRoleRepository roleRepository;

	@Autowired
	private NexHomeFunctionRepository functionRepository;

	@Autowired
	private NexHomeRoleFunctionRepository roleFunctionRepository;

	@Autowired
	private NexHomeUserRepository userRepository;

	@Autowired
	private NexHomeUserDetailsRepository userDetailsRepository;

	@Autowired
	private NexHomeUserAddressRepository userAddressRepository;

	@Autowired
	private EntityManager entityManager;

	private long phoneCounter = 9100000000L;

	@Test
	void deletingUserRemovesTheirDetailsAndAddressesOnly() {
		NexHomeRole role = role();
		NexHomeUser user = userWithDetailsAndAddresses(role);
		NexHomeUser otherUser = userWithDetailsAndAddresses(role);
		entityManager.flush();

		userService.deleteUser(user.getUserId());

		assertThat(userRepository.findById(user.getUserId())).isEmpty();
		assertThat(userDetailsRepository.findByUserUserId(user.getUserId())).isEmpty();
		assertThat(addressCount(user)).isZero();
		assertThat(userRepository.findById(otherUser.getUserId())).isPresent();
		assertThat(userDetailsRepository.findByUserUserId(otherUser.getUserId())).isPresent();
		assertThat(addressCount(otherUser)).isEqualTo(2);
	}

	@Test
	void deletingRoleRemovesItsRoleFunctionsAndUsersWithTheirDetailsAndAddresses() {
		NexHomeRole role = role();
		NexHomeRole otherRole = role();
		NexHomeFunction function = function();
		NexHomeUser user1 = userWithDetailsAndAddresses(role);
		NexHomeUser user2 = userWithDetailsAndAddresses(role);
		NexHomeUser otherRoleUser = userWithDetailsAndAddresses(otherRole);
		NexHomeRoleFunction roleFunction = roleFunction(role, function);
		NexHomeRoleFunction otherRoleFunction = roleFunction(otherRole, function);
		entityManager.flush();

		roleService.deleteRole(role.getRoleId());

		assertThat(roleRepository.findById(role.getRoleId())).isEmpty();
		assertThat(roleFunctionRepository.findById(roleFunction.getRoleFunctionId())).isEmpty();
		for (NexHomeUser user : new NexHomeUser[] { user1, user2 }) {
			assertThat(userRepository.findById(user.getUserId())).isEmpty();
			assertThat(userDetailsRepository.findByUserUserId(user.getUserId())).isEmpty();
			assertThat(addressCount(user)).isZero();
		}
		// Another role, its user and the shared function are untouched
		assertThat(roleRepository.findById(otherRole.getRoleId())).isPresent();
		assertThat(userRepository.findById(otherRoleUser.getUserId())).isPresent();
		assertThat(addressCount(otherRoleUser)).isEqualTo(2);
		assertThat(roleFunctionRepository.findById(otherRoleFunction.getRoleFunctionId())).isPresent();
		assertThat(functionRepository.findById(function.getFunctionId())).isPresent();
	}

	@Test
	void deletingFunctionRemovesItsRoleFunctionsButNotTheRoles() {
		NexHomeRole role = role();
		NexHomeRole otherRole = role();
		NexHomeFunction function = function();
		NexHomeFunction otherFunction = function();
		NexHomeRoleFunction roleFunction = roleFunction(role, function);
		NexHomeRoleFunction otherRoleFunction = roleFunction(otherRole, function);
		NexHomeRoleFunction unrelated = roleFunction(role, otherFunction);
		entityManager.flush();

		functionService.deleteFunction(function.getFunctionId());

		assertThat(functionRepository.findById(function.getFunctionId())).isEmpty();
		assertThat(roleFunctionRepository.findById(roleFunction.getRoleFunctionId())).isEmpty();
		assertThat(roleFunctionRepository.findById(otherRoleFunction.getRoleFunctionId())).isEmpty();
		assertThat(roleFunctionRepository.findById(unrelated.getRoleFunctionId())).isPresent();
		assertThat(roleRepository.findById(role.getRoleId())).isPresent();
		assertThat(roleRepository.findById(otherRole.getRoleId())).isPresent();
	}

	@Test
	void deletingRoleFunctionRemovesOnlyThatRow() {
		NexHomeRole role = role();
		NexHomeFunction function = function();
		NexHomeRoleFunction roleFunction = roleFunction(role, function);
		entityManager.flush();

		roleService.deleteRoleFunction(roleFunction.getRoleFunctionId());

		assertThat(roleFunctionRepository.findById(roleFunction.getRoleFunctionId())).isEmpty();
		assertThat(roleRepository.findById(role.getRoleId())).isPresent();
		assertThat(functionRepository.findById(function.getFunctionId())).isPresent();
	}

	@Test
	void lockedIdentityColumnsAreNeverWrittenAfterInsert() {
		NexHomeRole role = role();
		NexHomeRole otherRole = role();
		NexHomeFunction function = function();
		NexHomeFunction otherFunction = function();
		NexHomeRoleFunction roleFunction = roleFunction(role, function);
		entityManager.flush();
		String originalModule = function.getFunctionModule();
		String originalCode = function.getFunctionCode();

		String originalRoleName = role.getRoleName();
		role.setRoleName("CHANGED_ROLE_" + System.nanoTime());
		role.setDescription("Changed description");
		function.setFunctionModule("CHANGED");
		function.setFunctionCode("CHANGED_" + System.nanoTime());
		function.setFunctionName("Changed name " + System.nanoTime());
		roleFunction.setRole(otherRole);
		roleFunction.setFunction(otherFunction);
		roleFunction.setWrite(true);
		entityManager.flush();
		entityManager.clear();

		NexHomeRole reloadedRole = roleRepository.findById(role.getRoleId()).orElseThrow();
		assertThat(reloadedRole.getRoleName()).isEqualTo(originalRoleName);
		assertThat(reloadedRole.getDescription()).isEqualTo("Changed description");
		NexHomeFunction reloadedFunction = functionRepository.findById(function.getFunctionId()).orElseThrow();
		assertThat(reloadedFunction.getFunctionModule()).isEqualTo(originalModule);
		assertThat(reloadedFunction.getFunctionCode()).isEqualTo(originalCode);
		assertThat(reloadedFunction.getFunctionName()).startsWith("Changed name");
		NexHomeRoleFunction reloadedRoleFunction = roleFunctionRepository.findById(roleFunction.getRoleFunctionId())
				.orElseThrow();
		assertThat(reloadedRoleFunction.getRole().getRoleId()).isEqualTo(role.getRoleId());
		assertThat(reloadedRoleFunction.getFunction().getFunctionId()).isEqualTo(function.getFunctionId());
		assertThat(reloadedRoleFunction.isWrite()).isTrue();
	}

	private long addressCount(NexHomeUser user) {
		return userAddressRepository.findAll().stream()
				.filter(address -> address.getUser().getUserId().equals(user.getUserId()))
				.count();
	}

	private NexHomeRole role() {
		NexHomeRole role = new NexHomeRole();
		role.setRoleName("TEST_ROLE_" + System.nanoTime());
		return roleRepository.save(role);
	}

	private NexHomeFunction function() {
		long unique = System.nanoTime();
		NexHomeFunction function = new NexHomeFunction();
		function.setFunctionModule("TEST");
		function.setFunctionCode("TEST_FN_" + unique);
		function.setFunctionName("Test function " + unique);
		return functionRepository.save(function);
	}

	private NexHomeRoleFunction roleFunction(NexHomeRole role, NexHomeFunction function) {
		NexHomeRoleFunction roleFunction = new NexHomeRoleFunction();
		roleFunction.setRole(role);
		roleFunction.setFunction(function);
		roleFunction.setRead(true);
		return roleFunctionRepository.save(roleFunction);
	}

	private NexHomeUser userWithDetailsAndAddresses(NexHomeRole role) {
		NexHomeUser user = new NexHomeUser();
		user.setPhone(String.valueOf(phoneCounter++));
		user.setPassword("not-a-real-hash");
		user.setRole(role);
		userRepository.save(user);

		NexHomeUserDetails details = new NexHomeUserDetails();
		details.setUser(user);
		details.setFirstName("Test");
		userDetailsRepository.save(details);

		userAddressRepository.save(address(user, "Home", true));
		userAddressRepository.save(address(user, "Office", false));
		return user;
	}

	private static NexHomeUserAddress address(NexHomeUser user, String address1, boolean active) {
		NexHomeUserAddress address = new NexHomeUserAddress();
		address.setUser(user);
		address.setAddress1(address1);
		address.setAddress2("MG Road");
		address.setCity("Hyderabad");
		address.setDistrict("Hyderabad");
		address.setState("Telangana");
		address.setPinCode("500001");
		address.setActiveFlag(active);
		return address;
	}
}
