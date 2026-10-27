package com.nexhome.app.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeLookupValueResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePartnerAvailabilityUpdateRequest;
import com.nexhome.app.dto.NexHomePartnerBankAccountCreateRequest;
import com.nexhome.app.dto.NexHomePartnerBankAccountResponse;
import com.nexhome.app.dto.NexHomePartnerBankAccountUpdateRequest;
import com.nexhome.app.dto.NexHomePartnerBankAccountVerifyRequest;
import com.nexhome.app.dto.NexHomePartnerDocumentCreateRequest;
import com.nexhome.app.dto.NexHomePartnerDocumentResponse;
import com.nexhome.app.dto.NexHomePartnerDocumentUpdateRequest;
import com.nexhome.app.dto.NexHomePartnerDocumentVerifyRequest;
import com.nexhome.app.dto.NexHomePartnerProfileCreateRequest;
import com.nexhome.app.dto.NexHomePartnerProfileFields;
import com.nexhome.app.dto.NexHomePartnerProfileResponse;
import com.nexhome.app.dto.NexHomePartnerProfileReviewRequest;
import com.nexhome.app.dto.NexHomePartnerProfileSubmitRequest;
import com.nexhome.app.dto.NexHomePartnerProfileUpdateRequest;
import com.nexhome.app.dto.NexHomePartnerServiceCreateRequest;
import com.nexhome.app.dto.NexHomePartnerServiceResponse;
import com.nexhome.app.dto.NexHomePartnerServiceUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomePartnerBankAccountMapper;
import com.nexhome.app.mapper.NexHomePartnerDocumentMapper;
import com.nexhome.app.mapper.NexHomePartnerProfileMapper;
import com.nexhome.app.mapper.NexHomePartnerServiceMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeLookupCodes.PartnerAvailabilityStatus;
import com.nexhome.app.model.NexHomeLookupCodes.PartnerOnboardingStatus;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeLookupCodes.VerificationStatus;
import com.nexhome.app.model.NexHomeLookupCodes.WeekendAvailability;
import com.nexhome.app.model.NexHomePartnerBankAccount;
import com.nexhome.app.model.NexHomePartnerDocument;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomePartnerService;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeService;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomePartnerOnboardingServiceImpl implements NexHomePartnerOnboardingService {

	static final String PARTNER_ONBOARDING_MANAGE = "PARTNER_ONBOARDING_MANAGE";

	/** The onboarding statuses a reviewer may move a profile to, by its current status. */
	private static final Map<String, Set<String>> REVIEW_TRANSITIONS = Map.of(
			PartnerOnboardingStatus.UNDER_REVIEW, Set.of(PartnerOnboardingStatus.APPROVED,
					PartnerOnboardingStatus.REJECTED, PartnerOnboardingStatus.PROFILE_INCOMPLETE,
					PartnerOnboardingStatus.TRAINING_REQUIRED),
			PartnerOnboardingStatus.TRAINING_REQUIRED, Set.of(PartnerOnboardingStatus.APPROVED,
					PartnerOnboardingStatus.REJECTED, PartnerOnboardingStatus.PROFILE_INCOMPLETE),
			PartnerOnboardingStatus.APPROVED, Set.of(PartnerOnboardingStatus.SUSPENDED),
			PartnerOnboardingStatus.SUSPENDED, Set.of(PartnerOnboardingStatus.APPROVED),
			PartnerOnboardingStatus.REJECTED, Set.of(PartnerOnboardingStatus.PROFILE_INCOMPLETE));

	/** Review decisions that must say why. */
	private static final Set<String> REASON_REQUIRED = Set.of(PartnerOnboardingStatus.REJECTED,
			PartnerOnboardingStatus.PROFILE_INCOMPLETE, PartnerOnboardingStatus.SUSPENDED);

	/** Statuses in which the partner may still edit the profile and submit it. */
	private static final Set<String> EDITABLE_BY_PARTNER = Set.of(PartnerOnboardingStatus.DRAFT,
			PartnerOnboardingStatus.PROFILE_INCOMPLETE);

	/** Decisions staff may record on a document or bank account (PENDING is only the initial state). */
	private static final List<String> VERIFICATION_DECISIONS = List.of(VerificationStatus.IN_REVIEW,
			VerificationStatus.VERIFIED, VerificationStatus.REJECTED, VerificationStatus.EXPIRED);

	private final NexHomePartnerProfileRepository partnerProfileRepository;
	private final NexHomePartnerDocumentRepository partnerDocumentRepository;
	private final NexHomePartnerBankAccountRepository partnerBankAccountRepository;
	private final NexHomeUserRepository userRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomePartnerProfileMapper partnerProfileMapper;
	private final NexHomePartnerDocumentMapper partnerDocumentMapper;
	private final NexHomePartnerBankAccountMapper partnerBankAccountMapper;
	private final NexHomeServiceCategoryRepository categoryRepository;
	private final NexHomeServiceRepository serviceRepository;
	private final NexHomePartnerServiceRepository partnerServiceRepository;
	private final NexHomePartnerServiceMapper partnerServiceMapper;

	// ------------------------------------------------------------------------------------------------------------
	// Partner profiles
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomePartnerProfileResponse createPartnerProfile(NexHomePartnerProfileCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeUser user = userRepository.findById(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));
		if (!userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(user.getId(),
				NexHomeRole.PARTNER)) {
			throw new InvalidRequestException("Partner profiles are only for accounts with the PARTNER role.");
		}
		// Every partner account has an empty DRAFT from the start (V16): creating the profile fills it in, keeping its
		// partner id. Only a profile that already has a name is a duplicate.
		NexHomePartnerProfile existing = partnerProfileRepository.findByUserId(user.getId()).orElse(null);
		if (existing != null && existing.getFirstName() != null) {
			throw new DuplicateResourceException("This account already has a partner profile.");
		}
		if (user.getId().equals(actingUserId) && request.registrationDate() != null) {
			throw new AccessDeniedException("Partners cannot set their own registration date.");
		}

		NexHomePartnerProfile profile = existing != null ? existing
				: partnerProfileMapper.toPartnerProfileEntity(user, actingUserId);
		applyFields(profile, request, user, actingUserId);
		if (existing == null) {
			profile = partnerProfileRepository.save(profile);
		}

		log.info("Partner profile {} filled in for User ID {}", profile.getPublicId(), user.getId());
		return toPartnerProfileDto(profile, actingUserId);
	}

	@Override
	@Transactional
	public NexHomePartnerProfileResponse updatePartnerProfile(NexHomePartnerProfileUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile profile = findPartnerProfile(request.partnerProfileId());
		NexHomeUser owner = profile.getUser();
		requireSelfOrCan(owner.getId(), NexHomeAccess.WRITE);
		if (owner.getId().equals(actingUserId)) {
			if (!EDITABLE_BY_PARTNER.contains(profile.getOnboardingStatus())) {
				throw new InvalidRequestException("The profile is " + profile.getOnboardingStatus()
						+ " and cannot be changed now. Contact support to update it.");
			}
			if (request.registrationDate() != null) {
				throw new AccessDeniedException("Partners cannot set their own registration date.");
			}
		}

		applyFields(profile, request, owner, actingUserId);
		// Only a draft may be incomplete (ck_nexhome_partner_profiles_submitted)
		if (!PartnerOnboardingStatus.DRAFT.equals(profile.getOnboardingStatus())) {
			requireReadyForReview(profile);
		}

		log.info("Partner profile {} updated", profile.getPublicId());
		return toPartnerProfileDto(profile, actingUserId);
	}

	@Override
	@Transactional
	public NexHomePartnerProfileResponse submitPartnerProfile(NexHomePartnerProfileSubmitRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile profile = findPartnerProfile(request.partnerProfileId());
		requireSelfOrCan(profile.getUser().getId(), NexHomeAccess.WRITE);
		if (!EDITABLE_BY_PARTNER.contains(profile.getOnboardingStatus())) {
			throw new InvalidRequestException("Only a DRAFT or PROFILE_INCOMPLETE profile can be submitted; this one is "
					+ profile.getOnboardingStatus() + ".");
		}
		requireReadyForReview(profile);

		profile.setOnboardingStatus(PartnerOnboardingStatus.UNDER_REVIEW);
		profile.setVerificationStatus(VerificationStatus.IN_REVIEW);
		profile.setRejectionReason(null);
		profile.setUpdatedBy(actingUserId);

		log.info("Partner profile {} submitted for review", profile.getPublicId());
		return toPartnerProfileDto(profile, actingUserId);
	}

	@Override
	@Transactional
	public NexHomePartnerProfileResponse reviewPartnerProfile(NexHomePartnerProfileReviewRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile profile = findPartnerProfile(request.partnerProfileId());
		if (profile.getUser().getId().equals(actingUserId)) {
			throw new AccessDeniedException("Nobody can review their own partner profile.");
		}
		String target = lookupService.validateCode(Types.PARTNER_ONBOARDING_STATUS, request.onboardingStatus(),
				"Onboarding status");
		String current = profile.getOnboardingStatus();
		if (!REVIEW_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
			throw new InvalidRequestException("A profile that is " + current + " cannot be moved to " + target + ".");
		}
		String reason = NexHomeText.trimToNull(request.rejectionReason());
		if (REASON_REQUIRED.contains(target) && reason == null) {
			throw new InvalidRequestException("Rejection reason is required to move a profile to " + target + ".");
		}

		switch (target) {
			case PartnerOnboardingStatus.APPROVED -> {
				if (!Boolean.TRUE.equals(profile.getAdult())) {
					throw new InvalidRequestException("Only a partner who is 18 or older can be approved.");
				}
				profile.setApprovedBy(actingUserId);
				profile.setApprovedAt(Instant.now());
				profile.setVerificationStatus(VerificationStatus.VERIFIED);
			}
			case PartnerOnboardingStatus.REJECTED -> profile.setVerificationStatus(VerificationStatus.REJECTED);
			case PartnerOnboardingStatus.PROFILE_INCOMPLETE -> profile.setVerificationStatus(VerificationStatus.PENDING);
			default -> {
				// TRAINING_REQUIRED keeps the verification in review; SUSPENDED keeps it as it was
			}
		}
		if (!PartnerOnboardingStatus.APPROVED.equals(target)) {
			// Only an approved partner can take work
			profile.setAvailabilityStatus(PartnerAvailabilityStatus.OFFLINE);
		}
		profile.setOnboardingStatus(target);
		profile.setRejectionReason(PartnerOnboardingStatus.APPROVED.equals(target) ? null : reason);
		profile.setUpdatedBy(actingUserId);

		log.info("Partner profile {} moved from {} to {} by User ID {}", profile.getPublicId(), current, target,
				actingUserId);
		return toPartnerProfileDto(profile, actingUserId);
	}

	@Override
	@Transactional
	public NexHomePartnerProfileResponse updatePartnerAvailability(NexHomePartnerAvailabilityUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile profile = findPartnerProfile(request.partnerProfileId());
		requireSelfOrCan(profile.getUser().getId(), NexHomeAccess.WRITE);
		String target = lookupService.validateCode(Types.PARTNER_AVAILABILITY_STATUS, request.availabilityStatus(),
				"Availability status");
		if (!PartnerOnboardingStatus.APPROVED.equals(profile.getOnboardingStatus())) {
			throw new InvalidRequestException("Only an APPROVED partner can change availability.");
		}

		profile.setAvailabilityStatus(target);
		profile.setUpdatedBy(actingUserId);

		log.info("Partner profile {} is now {}", profile.getPublicId(), target);
		return toPartnerProfileDto(profile, actingUserId);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePartnerProfileResponse getPartnerProfile(Long partnerProfileId) {
		NexHomePartnerProfile profile = findPartnerProfile(partnerProfileId);
		requireSelfOrCan(profile.getUser().getId(), NexHomeAccess.READ);
		return toPartnerProfileDto(profile, permissionService.currentUserId());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePartnerProfileResponse getCurrentPartnerProfile() {
		Long userId = permissionService.currentUserId();
		return partnerProfileRepository.findByUserId(userId)
				.map(profile -> toPartnerProfileDto(profile, userId))
				.orElseThrow(() -> new ResourceNotFoundException("You do not have a partner profile yet."));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomePartnerProfileResponse> getPartnerProfiles(Integer page, Integer size,
			String onboardingStatus, String verificationStatus, String availabilityStatus, String city,
			String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Long userId = permissionService.currentUserId();
		Page<NexHomePartnerProfile> profiles = partnerProfileRepository.search(
				optionalCode(Types.PARTNER_ONBOARDING_STATUS, onboardingStatus, "Onboarding status"),
				optionalCode(Types.VERIFICATION_STATUS, verificationStatus, "Verification status"),
				optionalCode(Types.PARTNER_AVAILABILITY_STATUS, availabilityStatus, "Availability status"),
				NexHomePaging.searchText(city), NexHomePaging.searchText(search), pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, profiles,
				profiles.stream().map(profile -> toPartnerProfileDto(profile, userId)).toList());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Documents
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomePartnerDocumentResponse addPartnerDocument(NexHomePartnerDocumentCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile partner = findPartnerProfile(request.partnerProfileId());
		requireSelfOrCan(partner.getUser().getId(), NexHomeAccess.WRITE);
		String documentType = lookupService.validateCode(Types.DOCUMENT_TYPE, request.documentType(),
				"Document type");

		NexHomePartnerDocument document = partnerDocumentRepository.save(
				partnerDocumentMapper.toPartnerDocumentEntity(request, partner, documentType, actingUserId));

		log.info("Document {} ({}) added to partner profile {}", document.getId(), documentType,
				partner.getPublicId());
		return partnerDocumentMapper.toPartnerDocumentDto(document);
	}

	@Override
	@Transactional
	public NexHomePartnerDocumentResponse updatePartnerDocument(NexHomePartnerDocumentUpdateRequest request) {
		NexHomePartnerDocument document = findPartnerDocument(request.partnerDocumentId());
		requireSelfOrCan(document.getPartner().getUser().getId(), NexHomeAccess.WRITE);
		if (VerificationStatus.VERIFIED.equals(document.getVerificationStatus())) {
			throw new InvalidRequestException("A verified document cannot be changed; add a new document instead.");
		}

		partnerDocumentMapper.updatePartnerDocumentEntity(document, request, permissionService.currentUserId());

		log.info("Document {} updated and sent back for verification", document.getId());
		return partnerDocumentMapper.toPartnerDocumentDto(document);
	}

	@Override
	@Transactional
	public NexHomePartnerDocumentResponse verifyPartnerDocument(NexHomePartnerDocumentVerifyRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerDocument document = findPartnerDocument(request.partnerDocumentId());
		if (document.getPartner().getUser().getId().equals(actingUserId)) {
			throw new AccessDeniedException("Nobody can verify their own documents.");
		}
		String target = verificationDecision(request.verificationStatus(), document.getVerificationStatus(),
				"Document");
		String reason = NexHomeText.trimToNull(request.rejectionReason());
		if (VerificationStatus.REJECTED.equals(target) && reason == null) {
			throw new InvalidRequestException("Rejection reason is required to reject a document.");
		}
		if (VerificationStatus.VERIFIED.equals(target) && document.getExpiryDate() != null
				&& document.getExpiryDate().isBefore(LocalDate.now())) {
			throw new InvalidRequestException("An expired document cannot be verified.");
		}

		document.setVerificationStatus(target);
		// verified_by / verified_at record who decided and when; IN_REVIEW is not a decision yet
		boolean decided = !VerificationStatus.IN_REVIEW.equals(target);
		document.setVerifiedBy(decided ? actingUserId : null);
		document.setVerifiedAt(decided ? Instant.now() : null);
		document.setRejectionReason(VerificationStatus.VERIFIED.equals(target) ? null : reason);
		document.setUpdatedBy(actingUserId);

		log.info("Document {} set to {} by User ID {}", document.getId(), target, actingUserId);
		return partnerDocumentMapper.toPartnerDocumentDto(document);
	}

	@Override
	@Transactional
	public void deletePartnerDocument(Long partnerDocumentId) {
		NexHomePartnerDocument document = findPartnerDocument(partnerDocumentId);
		requireSelfOrCan(document.getPartner().getUser().getId(), NexHomeAccess.DELETE);
		if (VerificationStatus.VERIFIED.equals(document.getVerificationStatus())) {
			throw new InvalidRequestException("A verified document cannot be deleted.");
		}
		partnerDocumentRepository.delete(document);
		log.info("Document {} deleted", partnerDocumentId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomePartnerDocumentResponse> getPartnerDocuments(Long partnerProfileId) {
		NexHomePartnerProfile partner = findPartnerProfile(partnerProfileId);
		requireSelfOrCan(partner.getUser().getId(), NexHomeAccess.READ);
		return partnerDocumentRepository.findByPartnerIdOrderByCreationDateDescIdDesc(partnerProfileId).stream()
				.map(partnerDocumentMapper::toPartnerDocumentDto)
				.toList();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Bank accounts
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomePartnerBankAccountResponse addPartnerBankAccount(NexHomePartnerBankAccountCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile partner = findPartnerProfile(request.partnerProfileId());
		requireSelfOrCan(partner.getUser().getId(), NexHomeAccess.WRITE);

		NexHomePartnerBankAccount account = partnerBankAccountMapper.toPartnerBankAccountEntity(request, partner,
				actingUserId);
		List<NexHomePartnerBankAccount> existing = partnerBankAccountRepository
				.findByPartnerIdOrderByPrimaryDescCreationDateDescIdDesc(partner.getId());
		// Numbers are encrypted with a random IV, so duplicates are found by decrypting the partner's few accounts
		String accountNumber = request.accountNumber().trim();
		boolean duplicate = existing.stream().anyMatch(other -> other.getIfscCode().equals(account.getIfscCode())
				&& partnerBankAccountMapper.accountNumber(other).equals(accountNumber));
		if (duplicate) {
			throw new DuplicateResourceException("This bank account has already been added.");
		}

		boolean hasPrimary = existing.stream().anyMatch(NexHomePartnerBankAccount::isPrimary);
		boolean makePrimary = request.isPrimary() == null ? !hasPrimary : request.isPrimary();
		if (makePrimary) {
			clearCurrentPrimary(partner.getId(), actingUserId);
		}
		account.setPrimary(makePrimary);
		NexHomePartnerBankAccount saved = partnerBankAccountRepository.save(account);

		log.info("Bank account {} added to partner profile {}", saved.getId(), partner.getPublicId());
		return partnerBankAccountMapper.toPartnerBankAccountDto(saved);
	}

	@Override
	@Transactional
	public NexHomePartnerBankAccountResponse updatePartnerBankAccount(
			NexHomePartnerBankAccountUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerBankAccount account = findPartnerBankAccount(request.partnerBankAccountId());
		requireSelfOrCan(account.getPartner().getUser().getId(), NexHomeAccess.WRITE);

		partnerBankAccountMapper.updatePartnerBankAccountEntity(account, request, actingUserId);
		if (Boolean.TRUE.equals(request.isPrimary()) && !account.isPrimary()) {
			clearCurrentPrimary(account.getPartner().getId(), actingUserId);
			account.setPrimary(true);
		}
		else if (Boolean.FALSE.equals(request.isPrimary())) {
			account.setPrimary(false);
		}

		log.info("Bank account {} updated", account.getId());
		return partnerBankAccountMapper.toPartnerBankAccountDto(account);
	}

	@Override
	@Transactional
	public NexHomePartnerBankAccountResponse verifyPartnerBankAccount(
			NexHomePartnerBankAccountVerifyRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerBankAccount account = findPartnerBankAccount(request.partnerBankAccountId());
		if (account.getPartner().getUser().getId().equals(actingUserId)) {
			throw new AccessDeniedException("Nobody can verify their own bank accounts.");
		}
		String target = verificationDecision(request.verificationStatus(), account.getVerificationStatus(),
				"Bank account");

		account.setVerificationStatus(target);
		account.setUpdatedBy(actingUserId);

		log.info("Bank account {} set to {} by User ID {}", account.getId(), target, actingUserId);
		return partnerBankAccountMapper.toPartnerBankAccountDto(account);
	}

	@Override
	@Transactional
	public void deletePartnerBankAccount(Long partnerBankAccountId) {
		NexHomePartnerBankAccount account = findPartnerBankAccount(partnerBankAccountId);
		requireSelfOrCan(account.getPartner().getUser().getId(), NexHomeAccess.DELETE);
		partnerBankAccountRepository.delete(account);
		log.info("Bank account {} deleted", partnerBankAccountId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomePartnerBankAccountResponse> getPartnerBankAccounts(Long partnerProfileId) {
		NexHomePartnerProfile partner = findPartnerProfile(partnerProfileId);
		requireSelfOrCan(partner.getUser().getId(), NexHomeAccess.READ);
		return partnerBankAccountRepository.findByPartnerIdOrderByPrimaryDescCreationDateDescIdDesc(partnerProfileId)
				.stream()
				.map(partnerBankAccountMapper::toPartnerBankAccountDto)
				.toList();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Services offered
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomePartnerServiceResponse addPartnerService(NexHomePartnerServiceCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile partner = findPartnerProfile(request.partnerProfileId());
		requireSelfOrCan(partner.getUser().getId(), NexHomeAccess.WRITE);
		requireEditableByOwner(partner, actingUserId);
		NexHomeService service = serviceRepository.findById(request.serviceId())
				.filter(NexHomeService::isActive)
				.orElseThrow(() -> new ResourceNotFoundException("Service does not exist or is inactive."));
		if (partnerServiceRepository.existsByPartnerIdAndServiceId(partner.getId(), service.getId())) {
			throw new DuplicateResourceException("The partner already offers this service.");
		}

		boolean hasPrimary = partnerServiceRepository.findByPartnerIdAndPrimaryServiceTrue(partner.getId()).isPresent();
		boolean makePrimary = request.isPrimaryService() == null ? !hasPrimary : request.isPrimaryService();
		if (makePrimary) {
			clearCurrentPrimaryService(partner.getId(), actingUserId);
		}
		NexHomePartnerService partnerService = partnerServiceMapper.toPartnerServiceEntity(request, partner, service,
				actingUserId);
		partnerService.setPrimaryService(makePrimary);
		partnerService = partnerServiceRepository.save(partnerService);

		log.info("Service {} added to partner profile {}", service.getPublicId(), partner.getPublicId());
		return partnerServiceMapper.toPartnerServiceDto(partnerService);
	}

	@Override
	@Transactional
	public NexHomePartnerServiceResponse updatePartnerService(NexHomePartnerServiceUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerService partnerService = findPartnerService(request.partnerServiceId());
		NexHomePartnerProfile partner = partnerService.getPartner();
		requireSelfOrCan(partner.getUser().getId(), NexHomeAccess.WRITE);
		requireEditableByOwner(partner, actingUserId);

		if (Boolean.TRUE.equals(request.isPrimaryService()) && !partnerService.isPrimaryService()) {
			clearCurrentPrimaryService(partner.getId(), actingUserId);
		}
		partnerServiceMapper.updatePartnerServiceEntity(partnerService, request, actingUserId);
		if (request.isPrimaryService() != null) {
			partnerService.setPrimaryService(request.isPrimaryService());
		}

		log.info("Partner service {} updated", partnerService.getId());
		return partnerServiceMapper.toPartnerServiceDto(partnerService);
	}

	@Override
	@Transactional
	public void deletePartnerService(Long partnerServiceId) {
		NexHomePartnerService partnerService = findPartnerService(partnerServiceId);
		requireSelfOrCan(partnerService.getPartner().getUser().getId(), NexHomeAccess.DELETE);
		requireEditableByOwner(partnerService.getPartner(), permissionService.currentUserId());
		partnerServiceRepository.delete(partnerService);
		log.info("Partner service {} deleted", partnerServiceId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomePartnerServiceResponse> getPartnerServices(Long partnerProfileId) {
		NexHomePartnerProfile partner = findPartnerProfile(partnerProfileId);
		requireSelfOrCan(partner.getUser().getId(), NexHomeAccess.READ);
		return partnerServiceRepository.findByPartnerIdOrderByPrimaryServiceDescCreationDateDescIdDesc(partnerProfileId)
				.stream()
				.map(partnerServiceMapper::toPartnerServiceDto)
				.toList();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Rules
	// ------------------------------------------------------------------------------------------------------------

	/** Like the profile itself, the partner changes their services only while it is a DRAFT or PROFILE_INCOMPLETE. */
	private static void requireEditableByOwner(NexHomePartnerProfile partner, Long actingUserId) {
		if (partner.getUser().getId().equals(actingUserId)
				&& !EDITABLE_BY_PARTNER.contains(partner.getOnboardingStatus())) {
			throw new InvalidRequestException("The profile is " + partner.getOnboardingStatus()
					+ " and cannot be changed now. Contact support to update it.");
		}
	}

	/** One primary service per partner: the current one is cleared and flushed first, like bank accounts. */
	private void clearCurrentPrimaryService(Long partnerId, Long actingUserId) {
		partnerServiceRepository.findByPartnerIdAndPrimaryServiceTrue(partnerId).ifPresent(current -> {
			current.setPrimaryService(false);
			current.setUpdatedBy(actingUserId);
			partnerServiceRepository.saveAndFlush(current);
		});
	}

	/** Validates the submitted fields and applies them, including the normalized JSON fields. */
	private void applyFields(NexHomePartnerProfile profile, NexHomePartnerProfileFields fields, NexHomeUser owner,
			Long actingUserId) {
		requireCode(Types.PREFERRED_CONTACT_LANGUAGE, fields.preferredContactLanguage(), "Preferred contact language");
		requireCode(Types.SKILL_CATEGORY, fields.skillCategory(), "Skill category");
		requireCode(Types.PREFERRED_ARRANGEMENT, fields.preferredArrangement(), "Preferred arrangement");
		requireCode(Types.PAYMENT_BASIS, fields.paymentBasis(), "Payment basis");
		requireCode(Types.WEEKEND_AVAILABILITY, fields.weekendAvailability(), "Weekend availability");
		// Optional: an empty relationship clears it
		optionalCode(Types.EMERGENCY_CONTACT_RELATIONSHIP, fields.emergencyContactRelationship(),
				"Emergency contact relationship");
		if ((fields.latitude() == null) != (fields.longitude() == null)) {
			throw new InvalidRequestException("Latitude and longitude must be given together.");
		}
		if (owner.getMobileNumber().equals(fields.alternateContactNumber())) {
			throw new InvalidRequestException("Alternate contact number must differ from the partner's mobile number.");
		}
		if (owner.getMobileNumber().equals(fields.emergencyContactNumber())) {
			throw new InvalidRequestException("Emergency contact number must differ from the partner's mobile number.");
		}

		// Everything that queries is done before the entity changes, so no half-applied update is flushed
		Map<String, List<String>> weekly = fields.weeklyAvailability() == null ? null
				: normalizeWeeklyAvailability(fields.weeklyAvailability());
		NexHomeServiceCategory mainCategory = fields.mainServiceCategoryId() == null ? null
				: categoryRepository.findByIdAndActiveTrue(fields.mainServiceCategoryId())
						.orElseThrow(() -> new InvalidRequestException(
								"Main service category must be an active service category."));

		partnerProfileMapper.applyPartnerProfileFields(profile, fields, actingUserId);
		if (fields.additionalSkills() != null) {
			List<String> skills = distinct(fields.additionalSkills());
			profile.setAdditionalSkills(skills.isEmpty() ? null : partnerProfileMapper.toJson(skills));
		}
		if (fields.serviceAreas() != null) {
			profile.setServiceAreas(partnerProfileMapper.toJson(distinct(fields.serviceAreas())));
		}
		if (weekly != null) {
			profile.setWeeklyAvailability(partnerProfileMapper.toJson(weekly));
		}
		if (mainCategory != null) {
			profile.setMainServiceCategory(mainCategory);
		}
	}

	/**
	 * What submission requires (and what a submitted profile must keep): every field the design marks NOT NULL, at
	 * least one service area and availability slot, an adult partner, and a weekly grid that agrees with the weekend
	 * availability.
	 */
	private void requireReadyForReview(NexHomePartnerProfile profile) {
		Map<String, Object> required = new LinkedHashMap<>();
		// Names are optional only on the DRAFT every partner account starts with (V16)
		required.put("firstName", profile.getFirstName());
		required.put("lastName", profile.getLastName());
		required.put("isAdult", profile.getAdult());
		required.put("preferredContactLanguage", profile.getPreferredContactLanguage());
		required.put("addressLine1", profile.getAddressLine1());
		required.put("locality", profile.getLocality());
		required.put("city", profile.getCity());
		required.put("state", profile.getState());
		required.put("postalCode", profile.getPostalCode());
		required.put("skillCategory", profile.getSkillCategory());
		required.put("yearsExperience", profile.getYearsExperience());
		required.put("mainServiceCategoryId", profile.getMainServiceCategory());
		required.put("preferredArrangement", profile.getPreferredArrangement());
		required.put("paymentBasis", profile.getPaymentBasis());
		required.put("expectedEarningsInr", profile.getExpectedEarningsInr());
		required.put("maxTravelKm", profile.getMaxTravelKm());
		required.put("weekendAvailability", profile.getWeekendAvailability());
		List<String> missing = required.entrySet().stream()
				.filter(entry -> entry.getValue() == null)
				.map(Map.Entry::getKey)
				.collect(Collectors.toCollection(ArrayList::new));
		if (partnerProfileMapper.readList(profile.getServiceAreas()).isEmpty()) {
			missing.add("serviceAreas");
		}
		Map<String, List<String>> weekly = partnerProfileMapper.readAvailability(profile.getWeeklyAvailability());
		if (weekly.isEmpty()) {
			missing.add("weeklyAvailability");
		}
		if (!missing.isEmpty()) {
			throw new InvalidRequestException("Complete these fields first: " + String.join(", ", missing) + ".");
		}
		if (!profile.getAdult()) {
			throw new InvalidRequestException("Partners must be 18 or older.");
		}

		boolean saturday = weekly.containsKey(DayOfWeek.SATURDAY.name());
		boolean sunday = weekly.containsKey(DayOfWeek.SUNDAY.name());
		boolean matches = switch (profile.getWeekendAvailability()) {
			case WeekendAvailability.SATURDAY -> saturday && !sunday;
			case WeekendAvailability.SUNDAY -> sunday && !saturday;
			case WeekendAvailability.NEITHER -> !saturday && !sunday;
			default -> true;
		};
		if (!matches) {
			throw new InvalidRequestException("Weekly availability does not match weekend availability "
					+ profile.getWeekendAvailability() + ": check the Saturday and Sunday slots.");
		}
	}

	/**
	 * Day names upper-cased and in week order, slot codes checked against the active AVAILABILITY_SLOT values and
	 * deduplicated in slot order; days without slots are dropped.
	 */
	private Map<String, List<String>> normalizeWeeklyAvailability(Map<String, List<String>> grid) {
		List<String> slotOrder = lookupService.getActiveLookupValues(Types.AVAILABILITY_SLOT).stream()
				.map(NexHomeLookupValueResponse::valueCode)
				.toList();
		Map<DayOfWeek, Set<String>> byDay = new EnumMap<>(DayOfWeek.class);
		for (Map.Entry<String, List<String>> entry : grid.entrySet()) {
			DayOfWeek day = Arrays.stream(DayOfWeek.values())
					.filter(candidate -> candidate.name().equalsIgnoreCase(entry.getKey().trim()))
					.findFirst()
					.orElseThrow(() -> new InvalidRequestException(
							"Weekly availability days must be MONDAY to SUNDAY."));
			for (String slot : entry.getValue() == null ? List.<String>of() : entry.getValue()) {
				String code = slot == null ? "" : slot.trim().toUpperCase(Locale.ROOT);
				if (!slotOrder.contains(code)) {
					throw new InvalidRequestException("Weekly availability slots must be one of: "
							+ String.join(", ", slotOrder) + ".");
				}
				byDay.computeIfAbsent(day, unused -> new TreeSet<>(
						(a, b) -> Integer.compare(slotOrder.indexOf(a), slotOrder.indexOf(b)))).add(code);
			}
		}
		Map<String, List<String>> normalized = new LinkedHashMap<>();
		byDay.forEach((day, slots) -> normalized.put(day.name(), List.copyOf(slots)));
		return normalized;
	}

	/** Trimmed, whitespace-collapsed and deduplicated case-insensitively, keeping the first spelling. */
	private static List<String> distinct(List<String> items) {
		Set<String> seen = new LinkedHashSet<>();
		List<String> result = new ArrayList<>();
		for (String item : items) {
			String value = NexHomeText.collapseWhitespace(item);
			if (value != null && seen.add(value.toLowerCase(Locale.ROOT))) {
				result.add(value);
			}
		}
		return result;
	}

	/** A staff decision on a document or bank account: a different VERIFICATION_STATUS other than PENDING. */
	private String verificationDecision(String requested, String current, String what) {
		String target = lookupService.validateCode(Types.VERIFICATION_STATUS, requested, "Verification status");
		if (!VERIFICATION_DECISIONS.contains(target)) {
			throw new InvalidRequestException("Verification status must be one of: "
					+ String.join(", ", VERIFICATION_DECISIONS) + ".");
		}
		if (target.equals(current)) {
			throw new InvalidRequestException(what + " is already " + current + ".");
		}
		return target;
	}

	/** A submitted lookup code, if any, must be an active value; blank is rejected (the field cannot be cleared). */
	private void requireCode(String typeCode, String code, String fieldName) {
		if (code != null) {
			lookupService.validateCode(typeCode, code, fieldName);
		}
	}

	/** An optional lookup filter or field: null when empty, otherwise validated. */
	private String optionalCode(String typeCode, String code, String fieldName) {
		return code == null || code.isBlank() ? null : lookupService.validateCode(typeCode, code, fieldName);
	}

	/**
	 * Only one primary account per partner: the current one is cleared and flushed before the new primary is written,
	 * because the one-primary index would otherwise reject it.
	 */
	private void clearCurrentPrimary(Long partnerId, Long actingUserId) {
		partnerBankAccountRepository.findByPartnerIdAndPrimaryTrue(partnerId).ifPresent(current -> {
			current.setPrimary(false);
			current.setUpdatedBy(actingUserId);
			partnerBankAccountRepository.saveAndFlush(current);
		});
	}

	/** The emergency contact is shared with others only with the partner's permission. */
	private NexHomePartnerProfileResponse toPartnerProfileDto(NexHomePartnerProfile profile, Long viewerId) {
		return partnerProfileMapper.toPartnerProfileDto(profile,
				profile.getUser().getId().equals(viewerId) || profile.isEmergencyContactPermission());
	}

	private void requireSelfOrCan(Long ownerId, NexHomeAccess access) {
		if (!permissionService.isSelfOrCan(ownerId, PARTNER_ONBOARDING_MANAGE, access.name())) {
			throw new AccessDeniedException("Not the owner and no " + PARTNER_ONBOARDING_MANAGE + " " + access
					+ " access.");
		}
	}

	private NexHomePartnerProfile findPartnerProfile(Long partnerProfileId) {
		return partnerProfileRepository.findById(partnerProfileId)
				.orElseThrow(() -> new ResourceNotFoundException("Partner profile does not exist."));
	}

	private NexHomePartnerDocument findPartnerDocument(Long partnerDocumentId) {
		return partnerDocumentRepository.findById(partnerDocumentId)
				.orElseThrow(() -> new ResourceNotFoundException("Partner document does not exist."));
	}

	private NexHomePartnerService findPartnerService(Long partnerServiceId) {
		return partnerServiceRepository.findById(partnerServiceId)
				.orElseThrow(() -> new ResourceNotFoundException("Partner service does not exist."));
	}

	private NexHomePartnerBankAccount findPartnerBankAccount(Long partnerBankAccountId) {
		return partnerBankAccountRepository.findById(partnerBankAccountId)
				.orElseThrow(() -> new ResourceNotFoundException("Partner bank account does not exist."));
	}
}
