package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nexhome.app.dto.NexHomeAuditEntry;
import com.nexhome.app.model.NexHomeAdminProfile;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserRole;
import com.nexhome.app.repository.NexHomeAdminProfileRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

class NexHomeAuditUsersTest {

	private static final Long ADMIN_ID = 20L;
	private static final Long CUSTOMER_ID = 31L;

	private NexHomeUserRepository userRepository;
	private NexHomeUserRoleRepository userRoleRepository;
	private NexHomeAdminProfileRepository adminProfileRepository;
	private NexHomeCustomerProfileRepository customerProfileRepository;
	private NexHomeAuditUsers auditUsers;

	@BeforeEach
	void setUp() {
		userRepository = mock(NexHomeUserRepository.class);
		userRoleRepository = mock(NexHomeUserRoleRepository.class);
		adminProfileRepository = mock(NexHomeAdminProfileRepository.class);
		customerProfileRepository = mock(NexHomeCustomerProfileRepository.class);
		auditUsers = new NexHomeAuditUsers(userRepository, userRoleRepository, adminProfileRepository,
				mock(NexHomePartnerProfileRepository.class), customerProfileRepository);
	}

	@Test
	void staffGoByTheirStaffProfileOnlyElseTheirMobileNumber() {
		NexHomeUser admin = user(ADMIN_ID, "9000000020");
		NexHomeUser customer = user(CUSTOMER_ID, "9000000031");
		when(userRoleRepository.findWithRoleByUserIdIn(any())).thenReturn(List.of(
				userRole(admin, role((short) 2, NexHomeRole.ADMIN, "Admin")),
				// The admin's account also holds CUSTOMER, with a named customer profile
				userRole(admin, role((short) 4, NexHomeRole.CUSTOMER, "Customer")),
				userRole(customer, role((short) 4, NexHomeRole.CUSTOMER, "Customer"))));
		// The admin's staff profile has no name yet
		NexHomeAdminProfile staffProfile = new NexHomeAdminProfile();
		staffProfile.setUser(admin);
		when(adminProfileRepository.findByUserIdIn(any())).thenReturn(List.of(staffProfile));
		when(customerProfileRepository.findByUserIdIn(any()))
				.thenReturn(List.of(customerProfile(admin, "Wrong", "Name"), customerProfile(customer, "Anitha", "Rao")));
		when(userRepository.findAllById(any())).thenReturn(List.of(admin, customer));

		List<NexHomeAuditEntry> entries = auditUsers.entries(List.of(
				new NexHomeAuditUsers.Line("PROFILE_CREATED", null, Instant.now(), ADMIN_ID),
				new NexHomeAuditUsers.Line("ADDRESS_ADDED", null, Instant.now(), CUSTOMER_ID),
				new NexHomeAuditUsers.Line("APPROVED", null, null, null)));

		assertThat(entries.get(0).byName()).isEqualTo("+91 9000000020");
		assertThat(entries.get(0).byRole()).isEqualTo("Admin");
		assertThat(entries.get(1).byName()).isEqualTo("Anitha Rao");
		assertThat(entries.get(1).byRole()).isEqualTo("Customer");
		assertThat(entries.get(2).byName()).isNull();
		assertThat(entries.get(2).byRole()).isNull();
	}

	private static NexHomeUser user(Long id, String mobileNumber) {
		NexHomeUser user = new NexHomeUser();
		user.setId(id);
		user.setCountryCode("+91");
		user.setMobileNumber(mobileNumber);
		return user;
	}

	private static NexHomeRole role(short id, String code, String name) {
		NexHomeRole role = new NexHomeRole();
		role.setId(id);
		role.setCode(code);
		role.setName(name);
		return role;
	}

	private static NexHomeUserRole userRole(NexHomeUser user, NexHomeRole role) {
		NexHomeUserRole userRole = new NexHomeUserRole();
		userRole.setUser(user);
		userRole.setRole(role);
		return userRole;
	}

	private static NexHomeCustomerProfile customerProfile(NexHomeUser user, String firstName, String lastName) {
		NexHomeCustomerProfile profile = new NexHomeCustomerProfile();
		profile.setUser(user);
		profile.setFirstName(firstName);
		profile.setLastName(lastName);
		return profile;
	}
}
