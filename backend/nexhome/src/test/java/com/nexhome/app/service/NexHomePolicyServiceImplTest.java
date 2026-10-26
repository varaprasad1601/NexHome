package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nexhome.app.dto.NexHomePolicyAcceptanceCreateRequest;
import com.nexhome.app.dto.NexHomePolicyAcceptanceResponse;
import com.nexhome.app.dto.NexHomePolicyDocumentCreateRequest;
import com.nexhome.app.dto.NexHomePolicyDocumentPublishRequest;
import com.nexhome.app.dto.NexHomePolicyDocumentResponse;
import com.nexhome.app.dto.NexHomePolicyDocumentUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomePolicyAcceptanceMapper;
import com.nexhome.app.mapper.NexHomePolicyDocumentMapper;
import com.nexhome.app.model.NexHomePolicyAcceptance;
import com.nexhome.app.model.NexHomePolicyDocument;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomePolicyAcceptanceRepository;
import com.nexhome.app.repository.NexHomePolicyDocumentRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class NexHomePolicyServiceImplTest {

	private static final Long USER_ID = 10L;
	private static final Instant EFFECTIVE = Instant.parse("2026-01-01T00:00:00Z");

	private NexHomePolicyDocumentRepository policyRepository;
	private NexHomePolicyAcceptanceRepository acceptanceRepository;
	private NexHomeUserRepository userRepository;
	private NexHomeUserRoleRepository userRoleRepository;
	private NexHomeLookupService lookupService;
	private NexHomePermissionService permissionService;
	private NexHomePolicyServiceImpl policyService;
	private NexHomeUser user;
	private List<NexHomePolicyDocument> inEffect;

	@BeforeEach
	void setUp() {
		policyRepository = mock(NexHomePolicyDocumentRepository.class);
		acceptanceRepository = mock(NexHomePolicyAcceptanceRepository.class);
		userRepository = mock(NexHomeUserRepository.class);
		userRoleRepository = mock(NexHomeUserRoleRepository.class);
		lookupService = mock(NexHomeLookupService.class);
		permissionService = mock(NexHomePermissionService.class);
		user = new NexHomeUser();
		user.setId(USER_ID);
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		inEffect = new ArrayList<>();
		when(policyRepository.findPublishedInEffect(any())).thenReturn(inEffect);
		when(policyRepository.save(any())).thenAnswer(invocation -> {
			NexHomePolicyDocument policy = invocation.getArgument(0);
			policy.setId(90L);
			return policy;
		});
		when(acceptanceRepository.save(any())).thenAnswer(invocation -> {
			NexHomePolicyAcceptance acceptance = invocation.getArgument(0);
			acceptance.setId(95L);
			return acceptance;
		});
		when(acceptanceRepository.findByUserIdAndPolicyIdIn(anyLong(), any())).thenReturn(List.of());
		when(lookupService.validateCode(any(), any(), any()))
				.thenAnswer(invocation -> invocation.<String>getArgument(1).trim());
		// The caller is user 10 unless a test says otherwise
		when(permissionService.currentUserId()).thenReturn(USER_ID);
		policyService = new NexHomePolicyServiceImpl(policyRepository, acceptanceRepository, userRepository,
				userRoleRepository, lookupService, permissionService, new NexHomePolicyDocumentMapper(),
				new NexHomePolicyAcceptanceMapper());
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Current versions
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void onlyLiteralAddressesAreParsed() {
		assertThat(NexHomePolicyServiceImpl.parseAddress("203.0.113.24").getHostAddress()).isEqualTo("203.0.113.24");
		assertThat(NexHomePolicyServiceImpl.parseAddress("2001:db8::1")).isNotNull();
		// Host names made of hex letters would otherwise be looked up in DNS
		assertThat(NexHomePolicyServiceImpl.parseAddress("cafe")).isNull();
		assertThat(NexHomePolicyServiceImpl.parseAddress("dead.beef")).isNull();
		assertThat(NexHomePolicyServiceImpl.parseAddress("localhost")).isNull();
		assertThat(NexHomePolicyServiceImpl.parseAddress(null)).isNull();
	}

	@Test
	void firstPublishedRowOfEachKeyIsTheCurrentVersion() {
		inEffect.addAll(List.of(
				policy(1L, "PRIVACY", "2.0", "BOTH", true),
				policy(2L, "PRIVACY", "1.0", "BOTH", true),
				policy(3L, "TERMS", "1.1", "CUSTOMER", true),
				policy(4L, "TERMS", "1.0", "CUSTOMER", true)));
		Instant before = Instant.now();

		assertThat(policyService.getCurrentPolicies(null))
				.extracting(NexHomePolicyDocumentResponse::policyDocumentId)
				.containsExactly(1L, 3L);
		assertThat(policyService.getCurrentPolicy("TERMS").version()).isEqualTo("1.1");

		ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
		verify(policyRepository, org.mockito.Mockito.times(2)).findPublishedInEffect(now.capture());
		assertThat(now.getValue()).isBetween(before, Instant.now());
	}

	@Test
	void unknownOrNotYetEffectivePolicyHasNoCurrentVersion() {
		inEffect.add(policy(1L, "PRIVACY", "1.0", "BOTH", true));

		assertThatThrownBy(() -> policyService.getCurrentPolicy("REFUNDS"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("No published version of this policy is in effect.");
	}

	@Test
	void currentPoliciesForAnAudienceIncludeBoth() {
		inEffect.addAll(List.of(
				policy(1L, "PARTNER_TERMS", "1.0", "PARTNER", true),
				policy(2L, "PRIVACY", "1.0", "BOTH", true),
				policy(3L, "TERMS", "1.0", "CUSTOMER", true)));

		assertThat(policyService.getCurrentPolicies(" CUSTOMER "))
				.extracting(NexHomePolicyDocumentResponse::policyKey).containsExactly("PRIVACY", "TERMS");
		assertThat(policyService.getCurrentPolicies("PARTNER"))
				.extracting(NexHomePolicyDocumentResponse::policyKey).containsExactly("PARTNER_TERMS", "PRIVACY");
		assertThat(policyService.getCurrentPolicies("BOTH"))
				.extracting(NexHomePolicyDocumentResponse::policyKey).containsExactly("PRIVACY");
		assertThat(policyService.getCurrentPolicies(" ")).hasSize(3);
	}

	@Test
	void olderVersionWithAnotherAudienceDoesNotLeakIntoTheFilter() {
		// The current TERMS version is for partners only; the older customer version must not count
		inEffect.addAll(List.of(
				policy(1L, "TERMS", "2.0", "PARTNER", true),
				policy(2L, "TERMS", "1.0", "CUSTOMER", true)));

		assertThat(policyService.getCurrentPolicies("CUSTOMER")).isEmpty();
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Maintaining versions
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void duplicateVersionIsRejectedAfterTrimming() {
		when(policyRepository.existsByPolicyKeyAndVersion("TERMS", "1.0")).thenReturn(true);

		assertThatThrownBy(() -> policyService.createPolicy(new NexHomePolicyDocumentCreateRequest(" TERMS ", "Terms",
				"CUSTOMER", " 1.0 ", "Text", EFFECTIVE)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("This version of the policy already exists");
		verify(policyRepository, never()).save(any());
	}

	@Test
	void newVersionIsUnpublishedWithStrippedContent() {
		NexHomePolicyDocumentResponse response = policyService.createPolicy(new NexHomePolicyDocumentCreateRequest(
				"TERMS", " Terms of use ", " CUSTOMER ", "1.0", "\n\n  1. Be nice.\n  2. Pay.\n\n", EFFECTIVE));

		assertThat(response.isPublished()).isFalse();
		assertThat(response.publishedAt()).isNull();
		assertThat(response.audience()).isEqualTo("CUSTOMER");
		assertThat(response.title()).isEqualTo("Terms of use");
		assertThat(response.content()).isEqualTo("1. Be nice.\n  2. Pay.");
	}

	@Test
	void publishedPolicyCannotBeChangedOrDeleted() {
		NexHomePolicyDocument published = existingPolicy(policy(1L, "TERMS", "1.0", "CUSTOMER", true));

		assertThatThrownBy(() -> policyService.updatePolicy(update(1L, "New title", null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("A published policy cannot be changed; create a new version instead.");
		assertThat(published.getTitle()).isEqualTo("TERMS 1.0");

		assertThatThrownBy(() -> policyService.deletePolicy(1L))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("A published policy cannot be deleted.");
		verify(policyRepository, never()).delete(any(NexHomePolicyDocument.class));

		assertThatThrownBy(() -> policyService.publishPolicy(new NexHomePolicyDocumentPublishRequest(1L)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Policy is already published.");
	}

	@Test
	void draftCanBeUpdatedPublishedAndDeleted() {
		NexHomePolicyDocument draft = existingPolicy(policy(1L, "TERMS", "1.1", "CUSTOMER", false));

		assertThat(policyService.updatePolicy(update(1L, " New title ", " BOTH ")).audience()).isEqualTo("BOTH");
		assertThat(draft.getTitle()).isEqualTo("New title");

		Instant before = Instant.now();
		NexHomePolicyDocumentResponse response = policyService.publishPolicy(
				new NexHomePolicyDocumentPublishRequest(1L));
		assertThat(response.isPublished()).isTrue();
		assertThat(response.publishedAt()).isBetween(before, Instant.now());
		assertThat(draft.getUpdatedBy()).isEqualTo(USER_ID);

		NexHomePolicyDocument other = existingPolicy(policy(2L, "TERMS", "1.2", "CUSTOMER", false));
		policyService.deletePolicy(2L);
		verify(policyRepository).delete(other);
	}

	@Test
	void unknownAudienceOnUpdateLeavesTheDraftUnchanged() {
		NexHomePolicyDocument draft = existingPolicy(policy(1L, "TERMS", "1.1", "CUSTOMER", false));
		doThrow(new InvalidRequestException("Audience must be one of: CUSTOMER, PARTNER, BOTH."))
				.when(lookupService).validateCode("POLICY_AUDIENCE", "ADMIN", "Audience");

		assertThatThrownBy(() -> policyService.updatePolicy(update(1L, "New title", "ADMIN")))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(draft.getTitle()).isEqualTo("TERMS 1.1");
		assertThat(draft.getUpdatedBy()).isNull();
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Acceptances
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void onlyTheCurrentVersionCanBeAccepted() {
		NexHomePolicyDocument current = existingPolicy(policy(2L, "TERMS", "2.0", "CUSTOMER", true));
		NexHomePolicyDocument older = existingPolicy(policy(1L, "TERMS", "1.0", "CUSTOMER", true));
		NexHomePolicyDocument draft = existingPolicy(policy(3L, "TERMS", "3.0", "CUSTOMER", false));
		NexHomePolicyDocument notInEffect = existingPolicy(policy(4L, "REFUNDS", "1.0", "CUSTOMER", true));
		inEffect.addAll(List.of(current, older));

		for (NexHomePolicyDocument policy : List.of(older, draft, notInEffect)) {
			assertThatThrownBy(() -> policyService.acceptPolicy(accept(policy.getId()), "203.0.113.7"))
					.isInstanceOf(InvalidRequestException.class)
					.hasMessage("Only the current version of a policy can be accepted.");
		}
		verify(acceptanceRepository, never()).save(any());

		NexHomePolicyAcceptanceResponse response = policyService.acceptPolicy(accept(2L), "203.0.113.7");
		assertThat(response.policyDocumentId()).isEqualTo(2L);
		assertThat(response.userId()).isEqualTo(USER_ID);
		assertThat(response.acceptanceMethod()).isEqualTo("CHECKBOX");
		assertThat(response.version()).isEqualTo("2.0");
		assertThat(response.acceptedAt()).isNotNull();
	}

	@Test
	void policyCanBeAcceptedOnlyOnce() {
		inEffect.add(existingPolicy(policy(2L, "TERMS", "2.0", "CUSTOMER", true)));
		when(acceptanceRepository.existsByUserIdAndPolicyId(USER_ID, 2L)).thenReturn(true);

		assertThatThrownBy(() -> policyService.acceptPolicy(accept(2L), null))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("You have already accepted this policy version.");
		verify(acceptanceRepository, never()).save(any());
	}

	@Test
	void acceptanceNeedsAnExistingUserAndPolicy() {
		when(policyRepository.findById(404L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> policyService.acceptPolicy(accept(404L), null))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Policy document does not exist.");

		when(permissionService.currentUserId()).thenReturn(99L);
		when(userRepository.findById(99L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> policyService.acceptPolicy(accept(404L), null))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not exist.");
	}

	@Test
	void unknownAcceptanceMethodIsRejected() {
		inEffect.add(existingPolicy(policy(2L, "TERMS", "2.0", "CUSTOMER", true)));
		doThrow(new InvalidRequestException("Acceptance method must be one of: CHECKBOX, CLICKWRAP."))
				.when(lookupService).validateCode("ACCEPTANCE_METHOD", "SMOKE_SIGNAL", "Acceptance method");

		assertThatThrownBy(() -> policyService.acceptPolicy(new NexHomePolicyAcceptanceCreateRequest(2L,
				"SMOKE_SIGNAL"), null)).isInstanceOf(InvalidRequestException.class);
		verify(acceptanceRepository, never()).save(any());
	}

	@Test
	void onlyLiteralAddressesAreRecorded() {
		inEffect.add(existingPolicy(policy(2L, "TERMS", "2.0", "CUSTOMER", true)));

		assertThat(acceptedFrom("203.0.113.7")).isEqualTo("203.0.113.7");
		assertThat(acceptedFrom("2001:db8::1")).isEqualTo("2001:db8:0:0:0:0:0:1");
		assertThat(acceptedFrom("::1")).isEqualTo("0:0:0:0:0:0:0:1");
		// Names would need a lookup ("localhost" would resolve to 127.0.0.1): they are dropped instead
		assertThat(acceptedFrom("localhost")).isNull();
		assertThat(acceptedFrom("example.com")).isNull();
		assertThat(acceptedFrom("1.2.3.4; DROP TABLE")).isNull();
		assertThat(acceptedFrom("::zz")).isNull();
		assertThat(acceptedFrom("")).isNull();
		assertThat(acceptedFrom(null)).isNull();
	}

	@Test
	void acceptancesOfMissingUserAreNotFound() {
		when(userRepository.existsById(404L)).thenReturn(false);

		assertThatThrownBy(() -> policyService.getAcceptances(404L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("User does not exist.");
		verify(acceptanceRepository, never()).findByUserIdOrderByAcceptedAtDescIdDesc(any());
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Pending policies
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void customerSeesUnacceptedCustomerAndSharedPolicies() {
		holdsRoles(true, false);
		NexHomePolicyDocument terms = policy(1L, "TERMS", "1.0", "CUSTOMER", true);
		inEffect.addAll(List.of(policy(2L, "PARTNER_TERMS", "1.0", "PARTNER", true),
				policy(3L, "PRIVACY", "1.0", "BOTH", true), terms));
		acceptedAlready(terms);

		assertThat(policyService.getPendingPolicies())
				.extracting(NexHomePolicyDocumentResponse::policyKey).containsExactly("PRIVACY");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
		verify(acceptanceRepository).findByUserIdAndPolicyIdIn(eq(USER_ID), ids.capture());
		assertThat(ids.getValue()).containsExactly(3L, 1L);
	}

	@Test
	void partnerSeesPartnerAndSharedPolicies() {
		holdsRoles(false, true);
		inEffect.addAll(List.of(policy(2L, "PARTNER_TERMS", "1.0", "PARTNER", true),
				policy(3L, "PRIVACY", "1.0", "BOTH", true), policy(1L, "TERMS", "1.0", "CUSTOMER", true)));

		assertThat(policyService.getPendingPolicies())
				.extracting(NexHomePolicyDocumentResponse::policyKey).containsExactly("PARTNER_TERMS", "PRIVACY");
	}

	@Test
	void customerAndPartnerSeeEverything() {
		holdsRoles(true, true);
		inEffect.addAll(List.of(policy(2L, "PARTNER_TERMS", "1.0", "PARTNER", true),
				policy(3L, "PRIVACY", "1.0", "BOTH", true), policy(1L, "TERMS", "1.0", "CUSTOMER", true)));

		assertThat(policyService.getPendingPolicies()).hasSize(3);
	}

	@Test
	void staffWithoutCustomerOrPartnerRoleHaveNothingPending() {
		holdsRoles(false, false);
		inEffect.add(policy(3L, "PRIVACY", "1.0", "BOTH", true));

		assertThat(policyService.getPendingPolicies()).isEmpty();
		verify(policyRepository, never()).findPublishedInEffect(any());
	}

	@Test
	void nothingInEffectSkipsTheAcceptanceQuery() {
		holdsRoles(true, false);
		inEffect.add(policy(2L, "PARTNER_TERMS", "1.0", "PARTNER", true));

		assertThat(policyService.getPendingPolicies()).isEmpty();
		verify(acceptanceRepository, never()).findByUserIdAndPolicyIdIn(any(), any());
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Request validation
	// ----------------------------------------------------------------------------------------------------------------

	@Test
	void requestsValidateKeysAndVersionsAndRejectIdentityChanges() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(new NexHomePolicyDocumentCreateRequest("privacy", "Privacy",
					"BOTH", "-1.0", "Text", null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Policy key must contain only capital letters, digits and underscores, starting with a letter",
							"Version must contain only letters, digits, dots, hyphens and underscores",
							"Effective from is required");
			assertThat(factory.getValidator().validate(new NexHomePolicyDocumentCreateRequest("PRIVACY_2", "Privacy",
					"BOTH", "v2.0-rc_1", "Text", EFFECTIVE))).isEmpty();
			assertThat(factory.getValidator().validate(new NexHomePolicyDocumentUpdateRequest(1L, "TERMS", "2.0", true,
					" ", null, "", null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder("Policy key cannot be updated", "Version cannot be updated",
							"Use the publish endpoint to publish a policy", "Title cannot be blank",
							"Content cannot be blank");
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Helpers
	// ----------------------------------------------------------------------------------------------------------------

	private String acceptedFrom(String address) {
		ArgumentCaptor<NexHomePolicyAcceptance> saved = ArgumentCaptor.forClass(NexHomePolicyAcceptance.class);
		NexHomePolicyAcceptanceResponse response = policyService.acceptPolicy(accept(2L), address);
		verify(acceptanceRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
		assertThat(saved.getValue().getIpAddress() == null ? null : saved.getValue().getIpAddress().getHostAddress())
				.isEqualTo(response.ipAddress());
		return response.ipAddress();
	}

	private void holdsRoles(boolean customer, boolean partner) {
		when(userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(USER_ID, "CUSTOMER"))
				.thenReturn(customer);
		when(userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(USER_ID, "PARTNER"))
				.thenReturn(partner);
	}

	private void acceptedAlready(NexHomePolicyDocument policy) {
		NexHomePolicyAcceptance acceptance = new NexHomePolicyAcceptance();
		acceptance.setUser(user);
		acceptance.setPolicy(policy);
		when(acceptanceRepository.findByUserIdAndPolicyIdIn(eq(USER_ID), any())).thenReturn(List.of(acceptance));
	}

	private NexHomePolicyDocument existingPolicy(NexHomePolicyDocument policy) {
		when(policyRepository.findById(policy.getId())).thenReturn(Optional.of(policy));
		return policy;
	}

	private static NexHomePolicyDocument policy(Long id, String key, String version, String audience,
			boolean published) {
		NexHomePolicyDocument policy = new NexHomePolicyDocument();
		policy.setId(id);
		policy.setPolicyKey(key);
		policy.setVersion(version);
		policy.setTitle(key + " " + version);
		policy.setAudience(audience);
		policy.setContent("Text");
		policy.setEffectiveFrom(EFFECTIVE);
		policy.setPublished(published);
		if (published) {
			policy.setPublishedAt(EFFECTIVE);
		}
		return policy;
	}

	private static NexHomePolicyAcceptanceCreateRequest accept(Long policyDocumentId) {
		return new NexHomePolicyAcceptanceCreateRequest(policyDocumentId, " CHECKBOX ");
	}

	private static NexHomePolicyDocumentUpdateRequest update(Long id, String title, String audience) {
		return new NexHomePolicyDocumentUpdateRequest(id, null, null, null, title, audience, null, null);
	}
}
