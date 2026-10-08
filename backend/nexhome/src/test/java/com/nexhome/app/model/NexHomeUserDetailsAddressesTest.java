package com.nexhome.app.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeUserAddressRequest;
import com.nexhome.app.dto.NexHomeUserAddressUpdateRequest;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserAddressRepository;
import com.nexhome.app.repository.NexHomeUserDetailsRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.service.NexHomeUserService;

import jakarta.persistence.EntityManager;

/**
 * Runs against the dev database; every test is rolled back, so no data is kept.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class NexHomeUserDetailsAddressesTest {

	@Autowired
	private NexHomeRoleRepository roleRepository;

	@Autowired
	private NexHomeUserRepository userRepository;

	@Autowired
	private NexHomeUserDetailsRepository userDetailsRepository;

	@Autowired
	private NexHomeUserAddressRepository userAddressRepository;

	@Autowired
	private NexHomeUserService userService;

	@Autowired
	private EntityManager entityManager;

	@Test
	void addressesAreLoadedLazilyAndNeverChangedByUserDetails() {
		NexHomeUser user = newUser();
		saveAddress(user, "Old address", false);
		saveAddress(user, "Current address", true);
		NexHomeUserDetails details = new NexHomeUserDetails();
		details.setUser(user);
		details.setFirstName("Ravi");
		Long detailsId = userDetailsRepository.save(details).getUserDetailsId();
		entityManager.flush();
		entityManager.clear();

		NexHomeUserDetails loaded = userDetailsRepository.findById(detailsId).orElseThrow();
		assertThat(Hibernate.isInitialized(loaded.getAddresses())).isFalse();
		assertThat(loaded.getAddresses()).hasSize(2);
		assertThat(loaded.getAddresses()).filteredOn(NexHomeUserAddress::isActiveFlag)
				.singleElement()
				.extracting(NexHomeUserAddress::getAddress1)
				.isEqualTo("Current address");

		// Updating details, even after emptying the collection, must not touch address rows
		loaded.getAddresses().clear();
		loaded.setLastName("Kumar");
		entityManager.flush();
		entityManager.clear();

		assertThat(userAddressRepository.findAll())
				.filteredOn(address -> address.getUser().getUserId().equals(user.getUserId()))
				.hasSize(2);
	}

	@Test
	void secondActiveAddressForSameUserIsRejected() {
		NexHomeUser user = newUser();
		saveAddress(user, "First", true);
		entityManager.flush();

		// Rejected by the partial unique index on (user_id) WHERE active_flag, which PostgreSQL names in the error
		assertThatThrownBy(() -> {
			saveAddress(user, "Second", true);
			entityManager.flush();
		}).rootCause().hasMessageContaining("ux_nexhome_user_addresses_one_active");
	}

	@Test
	void addingActiveAddressThroughServiceSwitchesTheActiveOne() {
		NexHomeUser user = newUser();
		entityManager.flush();

		Long first = userService.addUserAddress(new NexHomeUserAddressRequest(user.getUserId(), "Old home", "MG Road", null,
				"Hyderabad", "Hyderabad", "Telangana", "500001", true)).userAddressId();
		Long second = userService.addUserAddress(new NexHomeUserAddressRequest(user.getUserId(), "New home", "MG Road", null,
				"Hyderabad", "Hyderabad", "Telangana", "500002", true)).userAddressId();
		entityManager.flush();
		entityManager.clear();

		assertThat(userAddressRepository.findById(first).orElseThrow().isActiveFlag()).isFalse();
		assertThat(userAddressRepository.findById(second).orElseThrow().isActiveFlag()).isTrue();
		assertThat(userAddressRepository.findByUserUserIdAndActiveFlag(user.getUserId(), true))
				.get().extracting(NexHomeUserAddress::getUserAddressId).isEqualTo(second);
	}

	@Test
	void activatingAnAddressThroughUpdateSwitchesTheActiveOne() {
		NexHomeUser user = newUser();
		saveAddress(user, "Home", true);
		saveAddress(user, "Office", false);
		entityManager.flush();
		Long homeId = userAddressRepository.findByUserUserIdAndActiveFlag(user.getUserId(), true)
				.orElseThrow().getUserAddressId();
		Long officeId = userAddressRepository.findAll().stream()
				.filter(a -> a.getUser().getUserId().equals(user.getUserId()) && !a.isActiveFlag())
				.findFirst().orElseThrow().getUserAddressId();

		userService.updateUserAddress(new NexHomeUserAddressUpdateRequest(officeId, null, null, null, null, null, null, null,
				null, true));
		entityManager.flush();
		entityManager.clear();

		assertThat(userAddressRepository.findById(homeId).orElseThrow().isActiveFlag()).isFalse();
		assertThat(userAddressRepository.findById(officeId).orElseThrow().isActiveFlag()).isTrue();
	}

	@Test
	void phoneAndRoleAreNeverWrittenAfterInsert() {
		NexHomeUser user = newUser();
		Long originalRoleId = user.getRole().getRoleId();
		NexHomeRole otherRole = new NexHomeRole();
		otherRole.setRoleName("TEST_OTHER_ROLE_" + System.nanoTime());
		roleRepository.save(otherRole);
		entityManager.flush();

		user.setPhone("9000000000");
		user.setRole(otherRole);
		user.setEmail("changed@example.com");
		entityManager.flush();
		entityManager.clear();

		NexHomeUser reloaded = userRepository.findById(user.getUserId()).orElseThrow();
		assertThat(reloaded.getPhone()).isEqualTo("9999999999");
		assertThat(reloaded.getRole().getRoleId()).isEqualTo(originalRoleId);
		assertThat(reloaded.getEmail()).isEqualTo("changed@example.com");
	}

	@Test
	void detailsAndAddressNeverMoveToAnotherUser() {
		NexHomeUser owner = newUser();
		NexHomeUser other = newUser();
		NexHomeUserDetails details = new NexHomeUserDetails();
		details.setUser(owner);
		details.setFirstName("Ravi");
		userDetailsRepository.save(details);
		saveAddress(owner, "Home", true);
		entityManager.flush();
		NexHomeUserAddress address = userAddressRepository.findByUserUserIdAndActiveFlag(owner.getUserId(), true)
				.orElseThrow();

		details.setUser(other);
		details.setLastName("Kumar");
		address.setUser(other);
		address.setAddress3("Near City Mall");
		entityManager.flush();
		entityManager.clear();

		NexHomeUserDetails reloadedDetails = userDetailsRepository.findById(details.getUserDetailsId()).orElseThrow();
		assertThat(reloadedDetails.getUser().getUserId()).isEqualTo(owner.getUserId());
		assertThat(reloadedDetails.getLastName()).isEqualTo("Kumar");
		NexHomeUserAddress reloadedAddress = userAddressRepository.findById(address.getUserAddressId()).orElseThrow();
		assertThat(reloadedAddress.getUser().getUserId()).isEqualTo(owner.getUserId());
		assertThat(reloadedAddress.getAddress3()).isEqualTo("Near City Mall");
	}

	private NexHomeUser newUser() {
		NexHomeRole role = new NexHomeRole();
		role.setRoleName("TEST_ROLE_" + System.nanoTime());
		roleRepository.save(role);
		NexHomeUser user = new NexHomeUser();
		user.setPhone("9999999999");
		user.setPassword("not-a-real-hash");
		user.setRole(role);
		return userRepository.save(user);
	}

	private void saveAddress(NexHomeUser user, String address1, boolean active) {
		NexHomeUserAddress address = new NexHomeUserAddress();
		address.setUser(user);
		address.setAddress1(address1);
		address.setAddress2("MG Road");
		address.setCity("Hyderabad");
		address.setDistrict("Hyderabad");
		address.setState("Telangana");
		address.setPinCode("500001");
		address.setActiveFlag(active);
		userAddressRepository.save(address);
	}
}
