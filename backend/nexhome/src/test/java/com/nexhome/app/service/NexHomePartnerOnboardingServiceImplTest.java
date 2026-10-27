package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.nexhome.app.config.NexHomeFieldEncryptor;
import com.nexhome.app.config.NexHomeSecurityProperties;
import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomePartnerBankAccountCreateRequest;
import com.nexhome.app.dto.NexHomePartnerBankAccountResponse;
import com.nexhome.app.dto.NexHomePartnerBankAccountUpdateRequest;
import com.nexhome.app.dto.NexHomePartnerBankAccountVerifyRequest;
import com.nexhome.app.dto.NexHomePartnerDocumentCreateRequest;
import com.nexhome.app.dto.NexHomePartnerDocumentUpdateRequest;
import com.nexhome.app.dto.NexHomePartnerDocumentVerifyRequest;
import com.nexhome.app.dto.NexHomePartnerProfileCreateRequest;
import com.nexhome.app.dto.NexHomePartnerProfileResponse;
import com.nexhome.app.dto.NexHomePartnerProfileReviewRequest;
import com.nexhome.app.dto.NexHomePartnerProfileSubmitRequest;
import com.nexhome.app.dto.NexHomePartnerProfileUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.mapper.NexHomePartnerBankAccountMapper;
import com.nexhome.app.mapper.NexHomePartnerDocumentMapper;
import com.nexhome.app.mapper.NexHomePartnerProfileMapper;
import com.nexhome.app.mapper.NexHomePartnerServiceMapper;
import com.nexhome.app.model.NexHomePartnerBankAccount;
import com.nexhome.app.model.NexHomePartnerDocument;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomeServiceCategory;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomePartnerBankAccountRepository;
import com.nexhome.app.repository.NexHomePartnerDocumentRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerServiceRepository;
import com.nexhome.app.repository.NexHomeServiceCategoryRepository;
import com.nexhome.app.repository.NexHomeServiceRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.json.JsonMapper;

class NexHomePartnerOnboardingServiceImplTest {

	private static final Long STAFF_ID = 1L;
	private static final Long OWNER_ID = 20L;
	private static final Long PROFILE_ID = 30L;
	private static final String OWNER_MOBILE = "9876543210";

	private static final List<String> ONBOARDING_STATUSES = List.of("DRAFT", "PROFILE_INCOMPLETE", "UNDER_REVIEW",
			"TRAINING_REQUIRED", "APPROVED", "REJECTED", "SUSPENDED");

	/** The review transitions the onboarding design allows, written out independently of the service. */
	private static final Map<String, Set<String>> ALLOWED = Map.of(
			"UNDER_REVIEW", Set.of("APPROVED", "REJECTED", "PROFILE_INCOMPLETE", "TRAINING_REQUIRED"),
			"TRAINING_REQUIRED", Set.of("APPROVED", "REJECTED", "PROFILE_INCOMPLETE"),
			"APPROVED", Set.of("SUSPENDED"),
			"SUSPENDED", Set.of("APPROVED"),
			"REJECTED", Set.of("PROFILE_INCOMPLETE"));

	private NexHomePartnerProfileRepository partnerProfileRepository;
	private NexHomePartnerDocumentRepository partnerDocumentRepository;
	private NexHomePartnerBankAccountRepository partnerBankAccountRepository;
	private NexHomeUserRepository userRepository;
	private NexHomeUserRoleRepository userRoleRepository;
	private NexHomeLookupService lookupService;
	private NexHomePermissionService permissionService;
	private NexHomePartnerProfileMapper partnerProfileMapper;
	private NexHomePartnerDocumentMapper partnerDocumentMapper;
	private NexHomePartnerBankAccountMapper partnerBankAccountMapper;
	private NexHomePartnerOnboardingServiceImpl partnerService;
	private NexHomeUser owner;

	@BeforeEach
	void setUp() {
		partnerProfileRepository = mock(NexHomePartnerProfileRepository.class);
		partnerDocumentRepository = mock(NexHomePartnerDocumentRepository.class);
		partnerBankAccountRepository = mock(NexHomePartnerBankAccountRepository.class);
		userRepository = mock(NexHomeUserRepository.class);
		userRoleRepository = mock(NexHomeUserRoleRepository.class);
		lookupService = mock(NexHomeLookupService.class);
		permissionService = mock(NexHomePermissionService.class);

		NexHomeFieldEncryptor encryptor = new NexHomeFieldEncryptor(new NexHomeSecurityProperties("unused", "nexhome",
				15, 30, List.of(), Base64.getEncoder().encodeToString(new byte[32])));
		partnerProfileMapper = new NexHomePartnerProfileMapper(JsonMapper.builder().build(), encryptor);
		partnerDocumentMapper = new NexHomePartnerDocumentMapper(encryptor);
		partnerBankAccountMapper = new NexHomePartnerBankAccountMapper(encryptor);

		owner = new NexHomeUser();
		owner.setId(OWNER_ID);
		owner.setCountryCode("+91");
		owner.setMobileNumber(OWNER_MOBILE);
		when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
		when(userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(OWNER_ID, "PARTNER"))
				.thenReturn(true);
		when(partnerProfileRepository.save(any())).thenAnswer(invocation -> {
			NexHomePartnerProfile profile = invocation.getArgument(0);
			profile.setId(PROFILE_ID);
			return profile;
		});
		when(partnerDocumentRepository.save(any())).thenAnswer(invocation -> {
			NexHomePartnerDocument document = invocation.getArgument(0);
			document.setId(40L);
			return document;
		});
		when(partnerBankAccountRepository.save(any())).thenAnswer(invocation -> {
			NexHomePartnerBankAccount account = invocation.getArgument(0);
			account.setId(50L);
			return account;
		});
		when(partnerBankAccountRepository.findByPartnerIdOrderByPrimaryDescCreationDateDescIdDesc(any()))
				.thenReturn(List.of());
		when(partnerBankAccountRepository.findByPartnerIdAndPrimaryTrue(any())).thenReturn(Optional.empty());

		// Every submitted lookup code is valid unless a test says otherwise
		when(lookupService.validateCode(any(), any(), any()))
				.thenAnswer(invocation -> invocation.<String>getArgument(1).trim());
		when(lookupService.getActiveLookupValues("AVAILABILITY_SLOT")).thenReturn(List.of(
				slot("MORNING", 1), slot("AFTERNOON", 2), slot("EVENING", 3)));

		// The caller is staff user 1 with PARTNER_ONBOARDING_MANAGE unless a test says otherwise
		when(permissionService.currentUserId()).thenReturn(STAFF_ID);
		when(permissionService.isSelfOrCan(any(), any(), any())).thenReturn(true);

		partnerService = new NexHomePartnerOnboardingServiceImpl(partnerProfileRepository, partnerDocumentRepository,
				partnerBankAccountRepository, userRepository, userRoleRepository, lookupService, permissionService,
				partnerProfileMapper, partnerDocumentMapper, partnerBankAccountMapper,
				mock(NexHomeServiceCategoryRepository.class), mock(NexHomeServiceRepository.class),
				mock(NexHomePartnerServiceRepository.class), new NexHomePartnerServiceMapper());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Review
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void reviewFollowsTheTransitionTable() {
		for (String current : ONBOARDING_STATUSES) {
			for (String target : ONBOARDING_STATUSES) {
				NexHomePartnerProfile profile = completeProfile(current);
				NexHomePartnerProfileReviewRequest request = new NexHomePartnerProfileReviewRequest(PROFILE_ID,
						target, "Reason");
				if (ALLOWED.getOrDefault(current, Set.of()).contains(target)) {
					assertThat(partnerService.reviewPartnerProfile(request).onboardingStatus())
							.as(current + " -> " + target)
							.isEqualTo(target);
				}
				else {
					assertThatThrownBy(() -> partnerService.reviewPartnerProfile(request))
							.as(current + " -> " + target)
							.isInstanceOf(InvalidRequestException.class)
							.hasMessage("A profile that is " + current + " cannot be moved to " + target + ".");
					assertThat(profile.getOnboardingStatus()).isEqualTo(current);
				}
			}
		}
	}

	@Test
	void rejectedProfileCanBeReopenedForChanges() {
		NexHomePartnerProfile profile = completeProfile("REJECTED");
		profile.setVerificationStatus("REJECTED");

		NexHomePartnerProfileResponse response = partnerService.reviewPartnerProfile(
				new NexHomePartnerProfileReviewRequest(PROFILE_ID, "PROFILE_INCOMPLETE", "  Upload a clearer photo  "));

		assertThat(response.onboardingStatus()).isEqualTo("PROFILE_INCOMPLETE");
		assertThat(response.verificationStatus()).isEqualTo("PENDING");
		assertThat(response.rejectionReason()).isEqualTo("Upload a clearer photo");
		assertThat(response.availabilityStatus()).isEqualTo("OFFLINE");
		assertThat(profile.getUpdatedBy()).isEqualTo(STAFF_ID);
	}

	@Test
	void approvalClearsTheReasonAndRecordsTheApprover() {
		NexHomePartnerProfile profile = completeProfile("SUSPENDED");
		profile.setRejectionReason("Customer complaints");
		profile.setVerificationStatus("VERIFIED");
		profile.setAvailabilityStatus("OFFLINE");
		Instant before = Instant.now();

		partnerService.reviewPartnerProfile(new NexHomePartnerProfileReviewRequest(PROFILE_ID, "APPROVED",
				"Ignored on approval"));

		assertThat(profile.getOnboardingStatus()).isEqualTo("APPROVED");
		assertThat(profile.getVerificationStatus()).isEqualTo("VERIFIED");
		assertThat(profile.getRejectionReason()).isNull();
		assertThat(profile.getApprovedBy()).isEqualTo(STAFF_ID);
		assertThat(profile.getApprovedAt()).isAfterOrEqualTo(before);
		assertThat(profile.getAvailabilityStatus()).isEqualTo("OFFLINE");
	}

	@Test
	void approvalNeedsAnAdultPartner() {
		for (Boolean adult : new Boolean[] { null, false }) {
			NexHomePartnerProfile profile = completeProfile("UNDER_REVIEW");
			profile.setAdult(adult);

			assertThatThrownBy(() -> partnerService.reviewPartnerProfile(
					new NexHomePartnerProfileReviewRequest(PROFILE_ID, "APPROVED", null)))
					.isInstanceOf(InvalidRequestException.class)
					.hasMessage("Only a partner who is 18 or older can be approved.");
			assertThat(profile.getOnboardingStatus()).isEqualTo("UNDER_REVIEW");
			assertThat(profile.getApprovedBy()).isNull();
		}
	}

	@ParameterizedTest
	@CsvSource({ "APPROVED, SUSPENDED, VERIFIED", "UNDER_REVIEW, TRAINING_REQUIRED, IN_REVIEW",
			"UNDER_REVIEW, REJECTED, REJECTED", "TRAINING_REQUIRED, PROFILE_INCOMPLETE, PENDING" })
	void everyDecisionButApprovalTakesThePartnerOffline(String current, String target, String verification) {
		NexHomePartnerProfile profile = completeProfile(current);
		profile.setVerificationStatus(current.equals("APPROVED") ? "VERIFIED" : "IN_REVIEW");
		profile.setAvailabilityStatus("ONLINE");

		partnerService.reviewPartnerProfile(new NexHomePartnerProfileReviewRequest(PROFILE_ID, target, "Reason"));

		assertThat(profile.getAvailabilityStatus()).isEqualTo("OFFLINE");
		assertThat(profile.getVerificationStatus()).isEqualTo(verification);
		assertThat(profile.getRejectionReason()).isEqualTo("Reason");
	}

	@ParameterizedTest
	@ValueSource(strings = { "PROFILE_INCOMPLETE", "SUSPENDED" })
	void blankReasonIsNotEnough(String target) {
		NexHomePartnerProfile profile = completeProfile(target.equals("SUSPENDED") ? "APPROVED" : "UNDER_REVIEW");

		assertThatThrownBy(() -> partnerService.reviewPartnerProfile(
				new NexHomePartnerProfileReviewRequest(PROFILE_ID, target, "   ")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Rejection reason is required to move a profile to " + target + ".");
		assertThat(profile.getOnboardingStatus()).isNotEqualTo(target);
	}

	@Test
	void unknownOnboardingStatusIsRejectedByTheLookup() {
		completeProfile("UNDER_REVIEW");
		when(lookupService.validateCode("PARTNER_ONBOARDING_STATUS", "NOPE", "Onboarding status"))
				.thenThrow(new InvalidRequestException("Onboarding status must be one of: DRAFT."));

		assertThatThrownBy(() -> partnerService.reviewPartnerProfile(
				new NexHomePartnerProfileReviewRequest(PROFILE_ID, "NOPE", null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Onboarding status must be one of: DRAFT.");
	}

	// ------------------------------------------------------------------------------------------------------------
	// Submission
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void submissionListsEveryMissingField() {
		NexHomePartnerProfile profile = partnerProfileMapper.toPartnerProfileEntity(owner, OWNER_ID);
		profile.setId(PROFILE_ID);
		profile.setFirstName("Suresh");
		profile.setLastName("Babu");
		when(partnerProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);

		assertThatThrownBy(() -> partnerService.submitPartnerProfile(new NexHomePartnerProfileSubmitRequest(PROFILE_ID)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Complete these fields first: isAdult, preferredContactLanguage, addressLine1, locality, "
						+ "city, state, postalCode, skillCategory, yearsExperience, mainServiceCategoryId, "
						+ "preferredArrangement, paymentBasis, "
						+ "expectedEarningsInr, maxTravelKm, weekendAvailability, serviceAreas, weeklyAvailability.");
		assertThat(profile.getOnboardingStatus()).isEqualTo("DRAFT");
	}

	@Test
	void submissionNeedsAnAdult() {
		completeProfile("DRAFT").setAdult(false);
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);

		assertThatThrownBy(() -> partnerService.submitPartnerProfile(new NexHomePartnerProfileSubmitRequest(PROFILE_ID)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Partners must be 18 or older.");
	}

	@ParameterizedTest
	@CsvSource({
			"NEITHER, '{\"MONDAY\":[\"MORNING\"]}', true",
			"NEITHER, '{\"MONDAY\":[\"MORNING\"],\"SATURDAY\":[\"MORNING\"]}', false",
			"NEITHER, '{\"SUNDAY\":[\"MORNING\"]}', false",
			"SATURDAY, '{\"SATURDAY\":[\"MORNING\"]}', true",
			"SATURDAY, '{\"SATURDAY\":[\"MORNING\"],\"SUNDAY\":[\"EVENING\"]}', false",
			"SATURDAY, '{\"MONDAY\":[\"MORNING\"]}', false",
			"SUNDAY, '{\"SUNDAY\":[\"MORNING\"]}', true",
			"SUNDAY, '{\"SATURDAY\":[\"MORNING\"]}', false",
			"SUNDAY, '{\"MONDAY\":[\"MORNING\"]}', false",
			"VARIES, '{\"SATURDAY\":[\"MORNING\"],\"SUNDAY\":[\"EVENING\"]}', true",
			"VARIES, '{\"MONDAY\":[\"MORNING\"]}', true" })
	void weekendAnswerMustAgreeWithTheWeeklyGrid(String weekend, String weekly, boolean matches) {
		NexHomePartnerProfile profile = completeProfile("PROFILE_INCOMPLETE");
		profile.setWeekendAvailability(weekend);
		profile.setWeeklyAvailability(weekly);
		profile.setRejectionReason("Add your weekend slots");
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);
		NexHomePartnerProfileSubmitRequest request = new NexHomePartnerProfileSubmitRequest(PROFILE_ID);

		if (matches) {
			NexHomePartnerProfileResponse response = partnerService.submitPartnerProfile(request);
			assertThat(response.onboardingStatus()).isEqualTo("UNDER_REVIEW");
			assertThat(response.verificationStatus()).isEqualTo("IN_REVIEW");
			assertThat(response.rejectionReason()).isNull();
		}
		else {
			assertThatThrownBy(() -> partnerService.submitPartnerProfile(request))
					.isInstanceOf(InvalidRequestException.class)
					.hasMessage("Weekly availability does not match weekend availability " + weekend
							+ ": check the Saturday and Sunday slots.");
			assertThat(profile.getOnboardingStatus()).isEqualTo("PROFILE_INCOMPLETE");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "UNDER_REVIEW", "TRAINING_REQUIRED", "APPROVED", "REJECTED", "SUSPENDED" })
	void onlyDraftOrIncompleteProfilesAreSubmitted(String status) {
		completeProfile(status);

		assertThatThrownBy(() -> partnerService.submitPartnerProfile(new NexHomePartnerProfileSubmitRequest(PROFILE_ID)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Only a DRAFT or PROFILE_INCOMPLETE profile can be submitted; this one is " + status + ".");
	}

	// ------------------------------------------------------------------------------------------------------------
	// Editing
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void weeklyAvailabilityIsNormalized() {
		NexHomePartnerProfile profile = completeProfile("DRAFT");
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);
		Map<String, List<String>> grid = new LinkedHashMap<>();
		grid.put("sunday", List.of(" evening "));
		grid.put("WEDNESDAY", List.of());
		grid.put(" Monday ", List.of("EVENING", "morning", "Evening", "AFTERNOON"));
		grid.put("friday", null);
		PartnerFields fields = new PartnerFields();
		fields.weeklyAvailability = grid;

		NexHomePartnerProfileResponse response = partnerService.updatePartnerProfile(fields.toUpdate());

		assertThat(profile.getWeeklyAvailability())
				.isEqualTo("{\"MONDAY\":[\"MORNING\",\"AFTERNOON\",\"EVENING\"],\"SUNDAY\":[\"EVENING\"]}");
		assertThat(response.weeklyAvailability()).containsOnlyKeys("MONDAY", "SUNDAY");
	}

	@Test
	void unknownSlotListsTheSlotsAndChangesNothing() {
		NexHomePartnerProfile profile = completeProfile("DRAFT");
		String before = profile.getWeeklyAvailability();
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);
		PartnerFields fields = new PartnerFields();
		fields.firstName = "Ramesh";
		fields.weeklyAvailability = Map.of("MONDAY", List.of("MORNING", "NIGHT"));

		assertThatThrownBy(() -> partnerService.updatePartnerProfile(fields.toUpdate()))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Weekly availability slots must be one of: MORNING, AFTERNOON, EVENING.");
		assertThat(profile.getFirstName()).isEqualTo("Suresh");
		assertThat(profile.getWeeklyAvailability()).isEqualTo(before);
	}

	@Test
	void listsAreDeduplicatedAndEmptySkillsAreCleared() {
		NexHomePartnerProfile profile = completeProfile("DRAFT");
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);
		PartnerFields fields = new PartnerFields();
		fields.additionalSkills = List.of(" Tiling ", "tiling", "Plumbing   work");

		assertThat(partnerService.updatePartnerProfile(fields.toUpdate()).additionalSkills())
				.containsExactly("Tiling", "Plumbing work");

		fields.additionalSkills = List.of();
		assertThat(partnerService.updatePartnerProfile(fields.toUpdate()).additionalSkills()).isNull();
		assertThat(profile.getAdditionalSkills()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "UNDER_REVIEW", "TRAINING_REQUIRED", "APPROVED", "REJECTED", "SUSPENDED" })
	void ownerCannotEditOnceSubmitted(String status) {
		NexHomePartnerProfile profile = completeProfile(status);
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);
		PartnerFields fields = new PartnerFields();
		fields.city = "Pune";

		assertThatThrownBy(() -> partnerService.updatePartnerProfile(fields.toUpdate()))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("The profile is " + status + " and cannot be changed now. Contact support to update it.");
		assertThat(profile.getCity()).isEqualTo("Hyderabad");
	}

	@ParameterizedTest
	@ValueSource(strings = { "DRAFT", "PROFILE_INCOMPLETE" })
	void ownerEditsDraftOrIncompleteProfile(String status) {
		NexHomePartnerProfile profile = completeProfile(status);
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);
		PartnerFields fields = new PartnerFields();
		fields.city = "Pune";

		partnerService.updatePartnerProfile(fields.toUpdate());

		assertThat(profile.getCity()).isEqualTo("Pune");
		assertThat(profile.getUpdatedBy()).isEqualTo(OWNER_ID);
	}

	@Test
	void staffEditSubmittedProfilesButMustKeepThemComplete() {
		NexHomePartnerProfile profile = completeProfile("APPROVED");
		PartnerFields fields = new PartnerFields();
		fields.city = "Pune";
		fields.registrationDate = LocalDate.of(2026, 1, 1);

		partnerService.updatePartnerProfile(fields.toUpdate());

		assertThat(profile.getCity()).isEqualTo("Pune");
		assertThat(profile.getRegistrationDate()).isEqualTo(LocalDate.of(2026, 1, 1));
		assertThat(profile.getUpdatedBy()).isEqualTo(STAFF_ID);

		PartnerFields mismatch = new PartnerFields();
		mismatch.weekendAvailability = "NEITHER";
		assertThatThrownBy(() -> partnerService.updatePartnerProfile(mismatch.toUpdate()))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Weekly availability does not match weekend availability NEITHER: check the Saturday and "
						+ "Sunday slots.");
	}

	@Test
	void editingNeedsOwnershipOrOnboardingManage() {
		NexHomePartnerProfile profile = completeProfile("DRAFT");
		when(permissionService.isSelfOrCan(OWNER_ID, "PARTNER_ONBOARDING_MANAGE", "WRITE")).thenReturn(false);
		PartnerFields fields = new PartnerFields();
		fields.city = "Pune";

		assertThatThrownBy(() -> partnerService.updatePartnerProfile(fields.toUpdate()))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> partnerService.submitPartnerProfile(new NexHomePartnerProfileSubmitRequest(PROFILE_ID)))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(profile.getCity()).isEqualTo("Hyderabad");
		assertThat(profile.getOnboardingStatus()).isEqualTo("DRAFT");
	}

	@Test
	void contactNumbersAndCoordinatesAreChecked() {
		completeProfile("DRAFT");
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);

		PartnerFields emergency = new PartnerFields();
		emergency.emergencyContactNumber = OWNER_MOBILE;
		assertThatThrownBy(() -> partnerService.updatePartnerProfile(emergency.toUpdate()))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Emergency contact number must differ from the partner's mobile number.");

		PartnerFields coordinates = new PartnerFields();
		coordinates.longitude = new BigDecimal("78.4");
		assertThatThrownBy(() -> partnerService.updatePartnerProfile(coordinates.toUpdate()))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Latitude and longitude must be given together.");
	}

	@Test
	void partnerCannotSetOwnRegistrationDateOnCreate() {
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);
		PartnerFields fields = new PartnerFields();
		fields.registrationDate = LocalDate.of(2026, 1, 1);

		assertThatThrownBy(() -> partnerService.createPartnerProfile(fields.toCreate()))
				.isInstanceOf(AccessDeniedException.class);
		verify(partnerProfileRepository, never()).save(any());

		// Staff registering the partner may backdate it
		when(permissionService.currentUserId()).thenReturn(STAFF_ID);
		NexHomePartnerProfileResponse created = partnerService.createPartnerProfile(fields.toCreate());
		assertThat(created.registrationDate()).isEqualTo(LocalDate.of(2026, 1, 1));
		assertThat(created.registeredBy()).isEqualTo(STAFF_ID);
		assertThat(created.onboardingStatus()).isEqualTo("DRAFT");
	}

	@Test
	void createNeedsPartnerRoleAndOnlyOneProfile() {
		when(userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(OWNER_ID, "PARTNER"))
				.thenReturn(false);
		assertThatThrownBy(() -> partnerService.createPartnerProfile(new PartnerFields().toCreate()))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Partner profiles are only for accounts with the PARTNER role.");

		when(userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(OWNER_ID, "PARTNER"))
				.thenReturn(true);
		// A profile with a name already is a duplicate
		NexHomePartnerProfile named = new NexHomePartnerProfile();
		named.setFirstName("Suresh");
		when(partnerProfileRepository.findByUserId(OWNER_ID)).thenReturn(Optional.of(named));
		assertThatThrownBy(() -> partnerService.createPartnerProfile(new PartnerFields().toCreate()))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("This account already has a partner profile.");
		verify(partnerProfileRepository, never()).save(any());
	}

	@Test
	void createFillsInTheDraftOfTheAccount() {
		when(userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(OWNER_ID, "PARTNER"))
				.thenReturn(true);
		// The empty DRAFT every partner account gets (V16)
		NexHomePartnerProfile draft = new NexHomePartnerProfile();
		draft.setId(90L);
		draft.setPublicId("NHPART0090");
		draft.setUser(userRepository.findById(OWNER_ID).orElseThrow());
		draft.setOnboardingStatus("DRAFT");
		draft.setVerificationStatus("PENDING");
		draft.setAvailabilityStatus("OFFLINE");
		when(partnerProfileRepository.findByUserId(OWNER_ID)).thenReturn(Optional.of(draft));

		var filled = partnerService.createPartnerProfile(new PartnerFields().toCreate());

		// The partner id stays; the draft is updated in place, not saved as a second profile
		assertThat(filled.publicId()).isEqualTo("NHPART0090");
		assertThat(draft.getFirstName()).isNotNull();
		verify(partnerProfileRepository, never()).save(any());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Documents
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void expiredDocumentCannotBeVerified() {
		NexHomePartnerDocument document = existingDocument("IN_REVIEW", LocalDate.now().minusDays(1));

		assertThatThrownBy(() -> partnerService.verifyPartnerDocument(
				new NexHomePartnerDocumentVerifyRequest(40L, "VERIFIED", null)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("An expired document cannot be verified.");
		assertThat(document.getVerificationStatus()).isEqualTo("IN_REVIEW");

		assertThat(partnerService.verifyPartnerDocument(
				new NexHomePartnerDocumentVerifyRequest(40L, "EXPIRED", null)).verificationStatus())
				.isEqualTo("EXPIRED");
	}

	@Test
	void documentExpiringTodayCanStillBeVerified() {
		existingDocument("PENDING", LocalDate.now());

		assertThat(partnerService.verifyPartnerDocument(
				new NexHomePartnerDocumentVerifyRequest(40L, "VERIFIED", null)).verificationStatus())
				.isEqualTo("VERIFIED");
	}

	@Test
	void rejectingDocumentNeedsAReason() {
		NexHomePartnerDocument document = existingDocument("PENDING", null);

		assertThatThrownBy(() -> partnerService.verifyPartnerDocument(
				new NexHomePartnerDocumentVerifyRequest(40L, "REJECTED", "  ")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Rejection reason is required to reject a document.");
		assertThat(document.getVerificationStatus()).isEqualTo("PENDING");

		partnerService.verifyPartnerDocument(new NexHomePartnerDocumentVerifyRequest(40L, "REJECTED", " Blurry "));
		assertThat(document.getVerificationStatus()).isEqualTo("REJECTED");
		assertThat(document.getRejectionReason()).isEqualTo("Blurry");
		assertThat(document.getVerifiedBy()).isEqualTo(STAFF_ID);
		assertThat(document.getVerifiedAt()).isNotNull();
	}

	@Test
	void inReviewIsNotADecision() {
		NexHomePartnerDocument document = existingDocument("REJECTED", null);
		document.setVerifiedBy(5L);
		document.setVerifiedAt(Instant.now());
		document.setRejectionReason("Blurry");

		partnerService.verifyPartnerDocument(new NexHomePartnerDocumentVerifyRequest(40L, "IN_REVIEW", null));

		assertThat(document.getVerificationStatus()).isEqualTo("IN_REVIEW");
		assertThat(document.getVerifiedBy()).isNull();
		assertThat(document.getVerifiedAt()).isNull();
		assertThat(document.getUpdatedBy()).isEqualTo(STAFF_ID);
	}

	@Test
	void verifiedDocumentLosesItsRejectionReason() {
		NexHomePartnerDocument document = existingDocument("REJECTED", null);
		document.setRejectionReason("Blurry");

		partnerService.verifyPartnerDocument(new NexHomePartnerDocumentVerifyRequest(40L, "VERIFIED", "ignored"));

		assertThat(document.getRejectionReason()).isNull();
		assertThat(document.getVerifiedBy()).isEqualTo(STAFF_ID);
	}

	@Test
	void sameVerificationStatusAgainIsRejected() {
		existingDocument("REJECTED", null);

		assertThatThrownBy(() -> partnerService.verifyPartnerDocument(
				new NexHomePartnerDocumentVerifyRequest(40L, "REJECTED", "Still blurry")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Document is already REJECTED.");
	}

	@Test
	void nobodyVerifiesTheirOwnDocumentsOrBankAccounts() {
		existingDocument("PENDING", null);
		existingBankAccount(51L, "123456784321", "SBIN0001234", true);
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);

		assertThatThrownBy(() -> partnerService.verifyPartnerDocument(
				new NexHomePartnerDocumentVerifyRequest(40L, "VERIFIED", null)))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> partnerService.verifyPartnerBankAccount(
				new NexHomePartnerBankAccountVerifyRequest(51L, "VERIFIED")))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void updatingRejectedDocumentSendsItBackForVerification() {
		NexHomePartnerDocument document = existingDocument("REJECTED", null);
		document.setVerifiedBy(5L);
		document.setVerifiedAt(Instant.now());
		document.setRejectionReason("Blurry");
		when(permissionService.currentUserId()).thenReturn(OWNER_ID);

		partnerService.updatePartnerDocument(new NexHomePartnerDocumentUpdateRequest(40L, null, null, null,
				"partners/30/pan-v2.pdf", null, null));

		assertThat(document.getVerificationStatus()).isEqualTo("PENDING");
		assertThat(document.getVerifiedBy()).isNull();
		assertThat(document.getVerifiedAt()).isNull();
		assertThat(document.getRejectionReason()).isNull();
		assertThat(document.getFileStorageKey()).isEqualTo("partners/30/pan-v2.pdf");
	}

	@Test
	void documentOfAnotherPartnerNeedsOnboardingManage() {
		completeProfile("DRAFT");
		when(permissionService.isSelfOrCan(OWNER_ID, "PARTNER_ONBOARDING_MANAGE", "WRITE")).thenReturn(false);

		assertThatThrownBy(() -> partnerService.addPartnerDocument(new NexHomePartnerDocumentCreateRequest(
				PROFILE_ID, "GOVERNMENT_ID", null, "k", null)))
				.isInstanceOf(AccessDeniedException.class);
		verify(partnerDocumentRepository, never()).save(any());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Bank accounts
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void firstBankAccountIsPrimaryUnlessDeclined() {
		completeProfile("DRAFT");

		assertThat(partnerService.addPartnerBankAccount(bankAccount("123456784321", "SBIN0001234", null))
				.isPrimary()).isTrue();
		verify(partnerBankAccountRepository, never()).saveAndFlush(any());

		NexHomePartnerBankAccountResponse declined = partnerService.addPartnerBankAccount(
				bankAccount("123456784321", "SBIN0001234", false));
		assertThat(declined.isPrimary()).isFalse();
	}

	@Test
	void laterBankAccountIsNotPrimaryUnlessAsked() {
		completeProfile("DRAFT");
		NexHomePartnerBankAccount current = existingBankAccount(51L, "123456784321", "SBIN0001234", true);
		when(partnerBankAccountRepository.findByPartnerIdOrderByPrimaryDescCreationDateDescIdDesc(PROFILE_ID))
				.thenReturn(List.of(current));
		when(partnerBankAccountRepository.findByPartnerIdAndPrimaryTrue(PROFILE_ID)).thenReturn(Optional.of(current));

		assertThat(partnerService.addPartnerBankAccount(bankAccount("998877665", "HDFC0000001", null)).isPrimary())
				.isFalse();
		assertThat(current.isPrimary()).isTrue();
		verify(partnerBankAccountRepository, never()).saveAndFlush(any());
	}

	@Test
	void newPrimaryClearsTheCurrentOneFirst() {
		completeProfile("DRAFT");
		NexHomePartnerBankAccount current = existingBankAccount(51L, "123456784321", "SBIN0001234", true);
		when(partnerBankAccountRepository.findByPartnerIdOrderByPrimaryDescCreationDateDescIdDesc(PROFILE_ID))
				.thenReturn(List.of(current));
		when(partnerBankAccountRepository.findByPartnerIdAndPrimaryTrue(PROFILE_ID)).thenReturn(Optional.of(current));

		assertThat(partnerService.addPartnerBankAccount(bankAccount("998877665", "HDFC0000001", true)).isPrimary())
				.isTrue();

		assertThat(current.isPrimary()).isFalse();
		assertThat(current.getUpdatedBy()).isEqualTo(STAFF_ID);
		InOrder order = inOrder(partnerBankAccountRepository);
		order.verify(partnerBankAccountRepository).saveAndFlush(current);
		order.verify(partnerBankAccountRepository).save(any());
	}

	@Test
	void sameAccountTwiceIsADuplicate() {
		completeProfile("DRAFT");
		NexHomePartnerBankAccount current = existingBankAccount(51L, "123456784321", "SBIN0001234", true);
		when(partnerBankAccountRepository.findByPartnerIdOrderByPrimaryDescCreationDateDescIdDesc(PROFILE_ID))
				.thenReturn(List.of(current));

		assertThatThrownBy(() -> partnerService.addPartnerBankAccount(
				bankAccount(" 123456784321 ", "sbin0001234", true)))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("This bank account has already been added.");
		assertThat(current.isPrimary()).isTrue();
		verify(partnerBankAccountRepository, never()).save(any());
		verify(partnerBankAccountRepository, never()).saveAndFlush(any());

		// The same number at another bank is a different account
		assertThatCode(() -> partnerService.addPartnerBankAccount(bankAccount("123456784321", "HDFC0000001", null)))
				.doesNotThrowAnyException();
	}

	@Test
	void updateMovesThePrimaryFlag() {
		NexHomePartnerBankAccount current = existingBankAccount(51L, "123456784321", "SBIN0001234", true);
		NexHomePartnerBankAccount other = existingBankAccount(52L, "998877665", "HDFC0000001", false);
		when(partnerBankAccountRepository.findByPartnerIdAndPrimaryTrue(PROFILE_ID)).thenReturn(Optional.of(current));

		assertThat(partnerService.updatePartnerBankAccount(bankUpdate(52L, true)).isPrimary()).isTrue();

		assertThat(other.isPrimary()).isTrue();
		assertThat(current.isPrimary()).isFalse();
		verify(partnerBankAccountRepository).saveAndFlush(current);
	}

	@Test
	void updateOfThePrimaryKeepsItWithoutClearing() {
		NexHomePartnerBankAccount current = existingBankAccount(51L, "123456784321", "SBIN0001234", true);
		when(partnerBankAccountRepository.findByPartnerIdAndPrimaryTrue(PROFILE_ID)).thenReturn(Optional.of(current));

		assertThat(partnerService.updatePartnerBankAccount(bankUpdate(51L, true)).isPrimary()).isTrue();
		assertThat(partnerService.updatePartnerBankAccount(bankUpdate(51L, null)).isPrimary()).isTrue();
		verify(partnerBankAccountRepository, never()).saveAndFlush(any());

		assertThat(partnerService.updatePartnerBankAccount(bankUpdate(51L, false)).isPrimary()).isFalse();
	}

	@Test
	void bankAccountVerificationRules() {
		NexHomePartnerBankAccount account = existingBankAccount(51L, "123456784321", "SBIN0001234", true);

		assertThatThrownBy(() -> partnerService.verifyPartnerBankAccount(
				new NexHomePartnerBankAccountVerifyRequest(51L, "PENDING")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Verification status must be one of: IN_REVIEW, VERIFIED, REJECTED, EXPIRED.");

		partnerService.verifyPartnerBankAccount(new NexHomePartnerBankAccountVerifyRequest(51L, " VERIFIED "));
		assertThat(account.getVerificationStatus()).isEqualTo("VERIFIED");
		assertThat(account.getUpdatedBy()).isEqualTo(STAFF_ID);

		assertThatThrownBy(() -> partnerService.verifyPartnerBankAccount(
				new NexHomePartnerBankAccountVerifyRequest(51L, "VERIFIED")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Bank account is already VERIFIED.");
	}

	// ------------------------------------------------------------------------------------------------------------
	// Request validation
	// ------------------------------------------------------------------------------------------------------------

	@Test
	void profileRequestValidatesAadhaarNamesAndContacts() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			PartnerFields valid = new PartnerFields();
			valid.firstName = "సురేష్";
			valid.lastName = "बाबू";
			valid.aadhaarNumber = "234567890123";
			assertThat(factory.getValidator().validate(valid.toCreate())).isEmpty();

			valid.aadhaarNumber = "";
			assertThat(factory.getValidator().validate(valid.toCreate())).isEmpty();

			PartnerFields invalid = new PartnerFields();
			invalid.firstName = "Suresh1";
			invalid.aadhaarNumber = "123456789012";
			invalid.alternateContactNumber = "5876543210";
			invalid.postalCode = "012345";
			assertThat(factory.getValidator().validate(invalid.toCreate()))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"First name must contain only letters, spaces, dots, apostrophes and hyphens",
							"Aadhaar number must be 12 digits and cannot start with 0 or 1",
							"Alternate contact number must be a 10-digit Indian mobile number",
							"Postal code must be a 6-digit PIN code");
		}
	}

	@Test
	void profileUpdateRejectsStatusAndOwnerFields() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			PartnerFields fields = new PartnerFields();
			fields.userId = 2L;
			fields.publicId = "NHPART0001";
			fields.onboardingStatus = "APPROVED";
			fields.verificationStatus = "VERIFIED";
			fields.availabilityStatus = "ONLINE";
			fields.aadhaarNumber = "23456789012";
			assertThat(factory.getValidator().validate(fields.toUpdate()))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"User cannot be updated",
							"Public ID cannot be updated",
							"Onboarding status cannot be updated here",
							"Verification status cannot be updated here",
							"Availability status cannot be updated here",
							"Aadhaar number must be 12 digits and cannot start with 0 or 1");
		}
	}

	@Test
	void bankAccountRequestsValidateIfscAndLockTheNumber() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(bankAccount("123456784321", "sbin0001234", null))).isEmpty();
			assertThat(factory.getValidator().validate(new NexHomePartnerBankAccountCreateRequest(PROFILE_ID,
					"Suresh", "12345678", "SBIN1001234", null, null)))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Account number must be 9 to 18 digits",
							"IFSC code must be 4 letters, a zero and 6 letters or digits");
			assertThat(factory.getValidator().validate(new NexHomePartnerBankAccountUpdateRequest(51L, PROFILE_ID,
					"123456784321", "SBIN0001234", " ", null, null, "VERIFIED")))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Partner profile cannot be updated",
							"Account number cannot be updated; add a new bank account instead",
							"IFSC code cannot be updated; add a new bank account instead",
							"Account holder name cannot be blank",
							"Verification status cannot be updated here");
		}
	}

	@Test
	void documentUpdateRejectsTypeAndStatus() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(new NexHomePartnerDocumentUpdateRequest(40L, PROFILE_ID,
					"OTHER", "ab", null, LocalDate.now().minusDays(1), "VERIFIED")))
					.extracting(ConstraintViolation::getMessage)
					.containsExactlyInAnyOrder(
							"Partner profile cannot be updated",
							"Document type cannot be updated",
							"Document number must be 4 to 30 letters, digits, spaces, slashes or hyphens",
							"Expiry date cannot be in the past",
							"Verification status cannot be updated here");
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------------------------------------------------

	private static NexHomeLookupValueResponse slot(String code, int order) {
		return new NexHomeLookupValueResponse((long) order, 99L, "AVAILABILITY_SLOT", code, code, null, order, true,
				false, Map.of(), null);
	}

	/** A profile owned by user 20 with every field that review needs, Saturdays included. */
	private NexHomePartnerProfile completeProfile(String onboardingStatus) {
		NexHomePartnerProfile profile = partnerProfileMapper.toPartnerProfileEntity(owner, OWNER_ID);
		profile.setId(PROFILE_ID);
		profile.setFirstName("Suresh");
		profile.setLastName("Babu");
		profile.setAdult(true);
		profile.setPreferredContactLanguage("te");
		profile.setAddressLine1("H.No 1-2-3");
		profile.setLocality("Madhapur");
		profile.setCity("Hyderabad");
		profile.setState("Telangana");
		profile.setPostalCode("500081");
		profile.setSkillCategory("SKILLED");
		profile.setYearsExperience((short) 6);
		NexHomeServiceCategory category = new NexHomeServiceCategory();
		category.setId(3L);
		profile.setMainServiceCategory(category);
		profile.setPreferredArrangement("FLEXIBLE_JOBS");
		profile.setPaymentBasis("PER_JOB");
		profile.setExpectedEarningsInr(new BigDecimal("800"));
		profile.setMaxTravelKm(new BigDecimal("12.5"));
		profile.setServiceAreas("[\"Madhapur\"]");
		profile.setWeeklyAvailability("{\"MONDAY\":[\"MORNING\"],\"SATURDAY\":[\"MORNING\"]}");
		profile.setWeekendAvailability("SATURDAY");
		profile.setOnboardingStatus(onboardingStatus);
		when(partnerProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
		return profile;
	}

	private NexHomePartnerDocument existingDocument(String verificationStatus, LocalDate expiryDate) {
		NexHomePartnerDocument document = partnerDocumentMapper.toPartnerDocumentEntity(
				new NexHomePartnerDocumentCreateRequest(PROFILE_ID, "GOVERNMENT_ID", "ABCDE1234F",
						"partners/30/pan.pdf", expiryDate),
				completeProfile("DRAFT"), "GOVERNMENT_ID", OWNER_ID);
		document.setId(40L);
		document.setVerificationStatus(verificationStatus);
		when(partnerDocumentRepository.findById(40L)).thenReturn(Optional.of(document));
		return document;
	}

	private NexHomePartnerBankAccount existingBankAccount(Long id, String number, String ifsc, boolean primary) {
		NexHomePartnerProfile partner = new NexHomePartnerProfile();
		partner.setId(PROFILE_ID);
		partner.setUser(owner);
		NexHomePartnerBankAccount account = partnerBankAccountMapper.toPartnerBankAccountEntity(
				bankAccount(number, ifsc, null), partner, OWNER_ID);
		account.setId(id);
		account.setPrimary(primary);
		when(partnerBankAccountRepository.findById(id)).thenReturn(Optional.of(account));
		return account;
	}

	private static NexHomePartnerBankAccountCreateRequest bankAccount(String number, String ifsc, Boolean primary) {
		return new NexHomePartnerBankAccountCreateRequest(PROFILE_ID, "Suresh Babu", number, ifsc, "Bank", primary);
	}

	private static NexHomePartnerBankAccountUpdateRequest bankUpdate(Long id, Boolean primary) {
		return new NexHomePartnerBankAccountUpdateRequest(id, null, null, null, null, null, primary, null);
	}

	/** Mutable stand-in for the 40-component partner profile requests; unset fields stay null. */
	private static final class PartnerFields {

		Long userId;
		String publicId;
		String onboardingStatus;
		String verificationStatus;
		String availabilityStatus;
		String firstName = "Suresh";
		String middleName;
		String lastName = "Babu";
		String aadhaarNumber;
		String alternateContactNumber;
		Boolean isAdult;
		String preferredContactLanguage;
		String addressLine1;
		String addressLine2;
		String landmark;
		String locality;
		String city;
		String state;
		String postalCode;
		BigDecimal latitude;
		BigDecimal longitude;
		LocalDate registrationDate;
		String skillCategory;
		Integer yearsExperience;
		Long mainServiceCategoryId;
		List<String> additionalSkills;
		String preferredArrangement;
		String paymentBasis;
		BigDecimal expectedEarningsInr;
		BigDecimal visitChargeInr;
		BigDecimal minimumJobPaymentInr;
		String previousWorkDetails;
		List<String> serviceAreas;
		BigDecimal maxTravelKm;
		Map<String, List<String>> weeklyAvailability;
		String weekendAvailability;
		String emergencyContactName;
		String emergencyContactRelationship;
		String emergencyContactNumber;
		Boolean emergencyContactPermission;

		NexHomePartnerProfileCreateRequest toCreate() {
			return new NexHomePartnerProfileCreateRequest(OWNER_ID, firstName, middleName, lastName, aadhaarNumber,
					alternateContactNumber, isAdult, preferredContactLanguage, addressLine1, addressLine2, landmark,
					locality, city, state, postalCode, latitude, longitude, registrationDate, skillCategory,
					yearsExperience, mainServiceCategoryId, additionalSkills, preferredArrangement, paymentBasis,
					expectedEarningsInr, visitChargeInr, minimumJobPaymentInr, previousWorkDetails, serviceAreas,
					maxTravelKm, weeklyAvailability(), weekendAvailability, emergencyContactName,
					emergencyContactRelationship, emergencyContactNumber, emergencyContactPermission);
		}

		/** The update leaves names alone unless a test set them to something else. */
		NexHomePartnerProfileUpdateRequest toUpdate() {
			return new NexHomePartnerProfileUpdateRequest(PROFILE_ID, userId, publicId, onboardingStatus,
					verificationStatus, availabilityStatus, "Suresh".equals(firstName) ? null : firstName, middleName,
					"Babu".equals(lastName) ? null : lastName, aadhaarNumber, alternateContactNumber, isAdult,
					preferredContactLanguage, addressLine1, addressLine2, landmark, locality, city, state, postalCode,
					latitude, longitude, registrationDate, skillCategory, yearsExperience, mainServiceCategoryId,
					additionalSkills, preferredArrangement, paymentBasis, expectedEarningsInr, visitChargeInr,
					minimumJobPaymentInr, previousWorkDetails, serviceAreas, maxTravelKm, weeklyAvailability(), weekendAvailability,
					emergencyContactName, emergencyContactRelationship, emergencyContactNumber,
					emergencyContactPermission);
		}

		/** A copy, so a grid holding null lists (which Map.of cannot) reaches the service as given. */
		private Map<String, List<String>> weeklyAvailability() {
			return weeklyAvailability == null ? null : new LinkedHashMap<>(weeklyAvailability);
		}
	}
}
