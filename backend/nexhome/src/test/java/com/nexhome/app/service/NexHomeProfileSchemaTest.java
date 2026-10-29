package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeAddress;
import com.nexhome.app.model.NexHomePartnerBankAccount;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAddressRepository;
import com.nexhome.app.repository.NexHomePartnerBankAccountRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomeRoleFunctionRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * Runs against the dev database (migrated by Flyway) and rolls back. Checks what mocks cannot for Module 2: the
 * generated public ids, the one-default and one-primary partial indexes, the check constraints on partner profiles,
 * and the seeded lookups and role functions.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class NexHomeProfileSchemaTest {

	@Autowired
	private NexHomeLookupService lookupService;
	@Autowired
	private NexHomeUserRepository userRepository;
	@Autowired
	private NexHomeUserMapper userMapper;
	@Autowired
	private NexHomeAddressRepository addressRepository;
	@Autowired
	private NexHomePartnerProfileRepository partnerProfileRepository;
	@Autowired
	private NexHomePartnerBankAccountRepository bankAccountRepository;
	@Autowired
	private NexHomeRoleFunctionRepository roleFunctionRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void seedsModuleTwoLookupsAndGrants() {
		assertThat(lookupService.getActiveLookupValues("PREFERRED_CONTACT_LANGUAGE"))
				.extracting(NexHomeLookupValueResponse::valueCode).startsWith("en", "hi", "te");
		assertThat(lookupService.getActiveLookupValues("AVAILABILITY_SLOT"))
				.extracting(NexHomeLookupValueResponse::valueCode)
				.containsExactly("EARLY_MORNING", "MORNING", "AFTERNOON", "EVENING", "NIGHT");
		assertThat(lookupService.getActiveLookupValues("AVAILABILITY_SLOT").getFirst().metadata())
				.containsEntry("start", "06:00");
		assertThat(roleFunctionRepository.findAll()).anySatisfy(grant -> {
			assertThat(grant.getRole().getCode()).isEqualTo("SUB_ADMIN");
			assertThat(grant.getFunction().getFunctionCode()).isEqualTo("PARTNER_ONBOARDING_MANAGE");
			assertThat(grant.isCanRead()).isTrue();
			assertThat(grant.isCanWrite()).isFalse();
		});
	}

	@Test
	void databaseGeneratesPublicIdsAndAllowsOneDefaultAddress() {
		NexHomeUser user = newUser();
		NexHomeAddress first = addressRepository.saveAndFlush(address(user, true));
		NexHomeAddress second = addressRepository.saveAndFlush(address(user, false));

		assertThat(first.getPublicId()).matches("NHADDR\\d{4,}");
		assertThat(second.getPublicId()).isNotEqualTo(first.getPublicId());

		second.setDefaultAddress(true);
		assertThatThrownBy(() -> addressRepository.flush())
				.isInstanceOfAny(DataIntegrityViolationException.class, PersistenceException.class);
	}

	@Test
	void partnerProfileChecksSubmissionAndPrimaryAccount() {
		NexHomePartnerProfile draft = partnerProfileRepository.saveAndFlush(draftProfile(newUser()));
		assertThat(draft.getPublicId()).matches("NHPART\\d{4,}");
		assertThat(draft.getRegistrationDate()).isNotNull();

		NexHomePartnerBankAccount primary = bankAccountRepository.saveAndFlush(bankAccount(draft, "1111"));
		NexHomePartnerBankAccount second = bankAccount(draft, "2222");
		assertThatThrownBy(() -> bankAccountRepository.saveAndFlush(second))
				.isInstanceOfAny(DataIntegrityViolationException.class, PersistenceException.class);
		assertThat(primary.isPrimary()).isTrue();
	}

	@Test
	void anIncompleteProfileCannotLeaveDraft() {
		NexHomePartnerProfile draft = partnerProfileRepository.saveAndFlush(draftProfile(newUser()));

		// ck_nexhome_partner_profiles_submitted: the design's NOT NULL columns are required once submitted
		assertThatThrownBy(() -> entityManager.createNativeQuery(
				"update nexhome_partner_profiles set onboarding_status = 'UNDER_REVIEW' where id = :id")
				.setParameter("id", draft.getId()).executeUpdate())
				.isInstanceOfAny(DataIntegrityViolationException.class, PersistenceException.class);
	}

	private NexHomeUser newUser() {
		return userRepository.saveAndFlush(userMapper.toUserEntity("+91",
				"8" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L), null, null, 1L));
	}

	private static NexHomeAddress address(NexHomeUser user, boolean isDefault) {
		NexHomeAddress address = new NexHomeAddress();
		address.setUser(user);
		address.setContactName("Ravi");
		address.setMobileNumber("9876543210");
		address.setAddressType("HOME");
		address.setAddressLine1("Plot 12");
		address.setCity("Hyderabad");
		address.setState("Telangana");
		address.setPostalCode("500081");
		address.setDefaultAddress(isDefault);
		return address;
	}

	private static NexHomePartnerProfile draftProfile(NexHomeUser user) {
		NexHomePartnerProfile profile = new NexHomePartnerProfile();
		profile.setUser(user);
		profile.setFirstName("Suresh");
		profile.setLastName("Babu");
		profile.setRegistrationDate(java.time.LocalDate.now());
		profile.setRegisteredBy(1L);
		profile.setOnboardingStatus("DRAFT");
		profile.setApprovalStatus("PENDING");
		profile.setAvailabilityStatus("OFFLINE");
		return profile;
	}

	private static NexHomePartnerBankAccount bankAccount(NexHomePartnerProfile partner, String last4) {
		NexHomePartnerBankAccount account = new NexHomePartnerBankAccount();
		account.setPartner(partner);
		account.setAccountHolderName("Suresh Babu");
		account.setAccountNumberEncrypted("v1:test");
		account.setAccountNumberLast4(last4);
		account.setIfscCode("SBIN0001234");
		account.setVerificationStatus("PENDING");
		account.setPrimary(true);
		return account;
	}
}
