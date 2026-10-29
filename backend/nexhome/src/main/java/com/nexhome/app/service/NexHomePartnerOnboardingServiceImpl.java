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
import com.nexhome.app.service.NexHomeAuditUsers.Line;
import com.nexhome.app.dto.NexHomeAuditEntry;
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
import com.nexhome.app.dto.NexHomePartnerProfileVerificationRequest;
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
import com.nexhome.app.mapper.NexHomeBookingMapper;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeLookupCodes.DocumentType;
import com.nexhome.app.model.NexHomeLookupCodes.PartnerAvailabilityStatus;
import com.nexhome.app.model.NexHomeLookupCodes.NotificationType;
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
	/** Staff changes to an approved partner (V25); before approval PARTNER_ONBOARDING_MANAGE write is enough. */
	static final String PARTNER_APPROVED_EDIT = "PARTNER_APPROVED_EDIT";

	/** The onboarding statuses a reviewer may move a profile to, by its current status. */
	private static final Map<String, Set<String>> REVIEW_TRANSITIONS = Map.of(
			PartnerOnboardingStatus.UNDER_REVIEW, Set.of(PartnerOnboardingStatus.APPROVED,
					PartnerOnboardingStatus.REJECTED, PartnerOnboardingStatus.CHANGES_REQUESTED),
			// An approved partner whose details need another look, or a rejected one given another chance
			PartnerOnboardingStatus.APPROVED, Set.of(PartnerOnboardingStatus.CHANGES_REQUESTED),
			PartnerOnboardingStatus.REJECTED, Set.of(PartnerOnboardingStatus.CHANGES_REQUESTED));

	/** Review decisions that must say why. */
	private static final Set<String> REASON_REQUIRED = Set.of(PartnerOnboardingStatus.REJECTED,
			PartnerOnboardingStatus.CHANGES_REQUESTED);

	/** Statuses of a profile not submitted yet: it may be incomplete (ck_nexhome_partner_profiles_submitted). */
	private static final Set<String> NOT_SUBMITTED = Set.of(PartnerOnboardingStatus.NOT_STARTED,
			PartnerOnboardingStatus.DRAFT);

	/** Statuses in which the partner may still edit the profile and submit it. */
	private static final Set<String> EDITABLE_BY_PARTNER = Set.of(PartnerOnboardingStatus.NOT_STARTED,
			PartnerOnboardingStatus.DRAFT, PartnerOnboardingStatus.CHANGES_REQUESTED);

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
	private final NexHomeAuditUsers auditUsers;
	private final NexHomeNotifier notifier;
	private final NexHomeAuditRecorder auditRecorder;

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
		// Every partner account has an empty NOT_STARTED profile from the start (V16, V18): creating the profile fills it
		// in, keeping its partner id, and makes it a DRAFT. Only a profile that already has a name is a duplicate.
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
		startDraft(profile);
		if (existing == null) {
			profile = partnerProfileRepository.save(profile);
		}

		log.info("Partner profile {} filled in for User ID {}", profile.getPublicId(), user.getId());
		return toProfileDto(profile);
	}

	@Override
	@Transactional
	public NexHomePartnerProfileResponse updatePartnerProfile(NexHomePartnerProfileUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile profile = findPartnerProfile(request.partnerProfileId());
		NexHomeUser owner = profile.getUser();
		requireCanChange(profile, NexHomeAccess.WRITE);
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
		startDraft(profile);
		// Only a profile not submitted yet may be incomplete (ck_nexhome_partner_profiles_submitted)
		if (!NOT_SUBMITTED.contains(profile.getOnboardingStatus())) {
			requireReadyForReview(profile);
		}

		log.info("Partner profile {} updated", profile.getPublicId());
		return toProfileDto(profile);
	}

	@Override
	@Transactional
	public NexHomePartnerProfileResponse submitPartnerProfile(NexHomePartnerProfileSubmitRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile profile = findPartnerProfile(request.partnerProfileId());
		requireSelfOrCan(profile.getUser().getId(), NexHomeAccess.WRITE);
		if (!EDITABLE_BY_PARTNER.contains(profile.getOnboardingStatus())) {
			throw new InvalidRequestException("Only a profile that is not submitted yet or has changes requested can be "
					+ "submitted; this one is " + profile.getOnboardingStatus() + ".");
		}
		requireReadyForReview(profile);

		profile.setOnboardingStatus(PartnerOnboardingStatus.UNDER_REVIEW);
		profile.setApprovalStatus(VerificationStatus.IN_REVIEW);
		profile.setRejectionReason(null);
		profile.setUpdatedBy(actingUserId);

		notifier.notifyStaff("PARTNER_ONBOARDING_MANAGE", new NexHomeNotifier.Message(NotificationType.ONBOARDING,
				"Partner " + profile.getPublicId() + " submitted for review",
				nameOf(profile) + " is waiting to be verified and approved.", NexHomeNotifier.PARTNER_PROFILE,
				profile.getId()), actingUserId);
		log.info("Partner profile {} submitted for review", profile.getPublicId());
		return toProfileDto(profile);
	}

	/** "First Last", else the partner id. */
	private static String nameOf(NexHomePartnerProfile profile) {
		String name = NexHomeBookingMapper.nameOf(profile.getFirstName(), profile.getLastName());
		return name == null ? profile.getPublicId() : name;
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
				Long partnerId = profile.getId();
				if (!profile.isAccountVerified()
						|| !documentsVerified(partnerDocumentRepository.findByPartnerIdIn(List.of(partnerId)))
						|| !bankAccountVerified(partnerBankAccountRepository.findByPartnerIdAndPrimaryTrue(partnerId)
								.orElse(null))) {
					throw new InvalidRequestException(
							"Verify the account, documents and bank account before approving the partner.");
				}
				profile.setApprovedBy(actingUserId);
				profile.setApprovedAt(Instant.now());
				profile.setApprovalStatus(VerificationStatus.VERIFIED);
			}
			case PartnerOnboardingStatus.REJECTED -> profile.setApprovalStatus(VerificationStatus.REJECTED);
			case PartnerOnboardingStatus.CHANGES_REQUESTED -> profile.setApprovalStatus(VerificationStatus.PENDING);
			default -> {
				// No other targets (REVIEW_TRANSITIONS)
			}
		}
		if (!PartnerOnboardingStatus.APPROVED.equals(target)) {
			// Only an approved partner can take work
			profile.setAvailabilityStatus(PartnerAvailabilityStatus.OFFLINE);
		}
		profile.setOnboardingStatus(target);
		profile.setRejectionReason(PartnerOnboardingStatus.APPROVED.equals(target) ? null : reason);
		profile.setUpdatedBy(actingUserId);

		String decision = switch (target) {
			case PartnerOnboardingStatus.APPROVED -> "Your partner profile is approved; you can now take jobs.";
			case PartnerOnboardingStatus.REJECTED -> "Your partner profile was rejected: " + reason;
			default -> "Changes were requested on your partner profile: " + reason;
		};
		notifier.notify(profile.getUser().getId(), new NexHomeNotifier.Message(NotificationType.ONBOARDING,
				"Onboarding " + NexHomeSupportServiceImpl.readable(target).toLowerCase(), decision,
				NexHomeNotifier.PARTNER_PROFILE, profile.getId()), actingUserId);
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("onboardingStatus", target);
		after.put("reason", profile.getRejectionReason());
		auditRecorder.record("ONBOARDING_DECISION", "PARTNER_PROFILE", profile.getPublicId(),
				Map.of("onboardingStatus", current), after, actingUserId);
		log.info("Partner profile {} moved from {} to {} by User ID {}", profile.getPublicId(), current, target,
				actingUserId);
		return toProfileDto(profile);
	}

	@Override
	@Transactional
	public NexHomePartnerProfileResponse updatePartnerVerifications(NexHomePartnerProfileVerificationRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile profile = findPartnerProfile(request.partnerProfileId());
		if (profile.getUser().getId().equals(actingUserId)) {
			throw new AccessDeniedException("Nobody can verify their own partner profile.");
		}
		// An approved partner was approved on this check; it cannot be taken back afterwards
		if (Boolean.FALSE.equals(request.accountVerified())
				&& PartnerOnboardingStatus.APPROVED.equals(profile.getOnboardingStatus())) {
			throw new InvalidRequestException("The account of an approved partner cannot be unverified.");
		}
		// The check names who made it; clearing it clears that too (ck_nexhome_partner_profiles_account_verified)
		if (request.accountVerified() != null && request.accountVerified() != profile.isAccountVerified()) {
			auditRecorder.record(request.accountVerified() ? "ACCOUNT_VERIFIED" : "ACCOUNT_UNVERIFIED",
					"PARTNER_PROFILE", profile.getPublicId(), null, null, actingUserId);
		}
		if (request.accountVerified() != null) {
			profile.setAccountVerified(request.accountVerified());
			profile.setAccountVerifiedBy(request.accountVerified() ? actingUserId : null);
			profile.setAccountVerifiedAt(request.accountVerified() ? Instant.now() : null);
		}
		profile.setUpdatedBy(actingUserId);

		log.info("Partner profile {} account verified: {}", profile.getPublicId(), profile.isAccountVerified());
		return toProfileDto(profile);
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
		return toProfileDto(profile);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePartnerProfileResponse getPartnerProfile(Long partnerProfileId) {
		NexHomePartnerProfile profile = findPartnerProfile(partnerProfileId);
		requireSelfOrCan(profile.getUser().getId(), NexHomeAccess.READ);
		return toProfileDto(profile);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePartnerProfileResponse getCurrentPartnerProfile() {
		Long userId = permissionService.currentUserId();
		return partnerProfileRepository.findByUserId(userId)
				.map(this::toProfileDto)
				.orElseThrow(() -> new ResourceNotFoundException("You do not have a partner profile yet."));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomePartnerProfileResponse> getPartnerProfiles(Integer page, Integer size,
			String onboardingStatus, String approvalStatus, String availabilityStatus, String city,
			String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Long userId = permissionService.currentUserId();
		Page<NexHomePartnerProfile> profiles = partnerProfileRepository.search(
				optionalCode(Types.PARTNER_ONBOARDING_STATUS, onboardingStatus, "Onboarding status"),
				optionalCode(Types.VERIFICATION_STATUS, approvalStatus, "Approval status"),
				optionalCode(Types.PARTNER_AVAILABILITY_STATUS, availabilityStatus, "Availability status"),
				NexHomePaging.searchText(city), NexHomePaging.searchText(search), pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, profiles,
				toProfileDtos(profiles.getContent()));
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeAuditEntry> getPartnerAudit(Long partnerProfileId) {
		NexHomePartnerProfile profile = findPartnerProfile(partnerProfileId);
		List<NexHomePartnerDocument> documents = partnerDocumentRepository
				.findByPartnerIdOrderByCreationDateDescIdDesc(partnerProfileId).reversed();
		List<NexHomePartnerBankAccount> accounts = partnerBankAccountRepository
				.findByPartnerIdOrderByPrimaryDescCreationDateDescIdDesc(partnerProfileId);

		List<Line> lines = new ArrayList<>();
		lines.add(new Line("PROFILE_CREATED", null, profile.getCreationDate(), profile.getCreatedBy()));
		lines.add(new Line("PROFILE_UPDATED", null, profile.getUpdatedDate(), profile.getUpdatedBy()));
		documents.forEach(document -> lines.add(new Line("DOCUMENT_ADDED", documentSubject(document),
				document.getCreationDate(), document.getCreatedBy())));
		accounts.forEach(account -> lines.add(new Line("BANK_ACCOUNT_ADDED", accountSubject(account),
				account.getCreationDate(), account.getCreatedBy())));
		lines.add(new Line("ACCOUNT_VERIFIED", null, profile.getAccountVerifiedAt(), profile.getAccountVerifiedBy()));
		documents.forEach(document -> {
			boolean verified = VerificationStatus.VERIFIED.equals(document.getVerificationStatus());
			lines.add(new Line("DOCUMENT_VERIFIED", documentSubject(document),
					verified ? document.getVerifiedAt() : null, verified ? document.getVerifiedBy() : null));
		});
		// A bank account has no verified by / at columns: its last update is the verification (a later change of the
		// holder name resets it to PENDING)
		accounts.forEach(account -> {
			boolean verified = VerificationStatus.VERIFIED.equals(account.getVerificationStatus());
			lines.add(new Line("BANK_ACCOUNT_VERIFIED", accountSubject(account),
					verified ? account.getUpdatedDate() : null, verified ? account.getUpdatedBy() : null));
		});
		lines.add(new Line("APPROVED", null, profile.getApprovedAt(), profile.getApprovedBy()));

		return auditUsers.entries(lines);
	}

	/** A document in the audit trail: its name, else its ID type, else its document type (codes). */
	private static String documentSubject(NexHomePartnerDocument document) {
		if (document.getDocumentName() != null) {
			return document.getDocumentName();
		}
		return document.getIdType() != null ? document.getIdType() : document.getDocumentType();
	}

	private static String accountSubject(NexHomePartnerBankAccount account) {
		return (account.getBankName() == null ? "Account" : account.getBankName()) + " ending "
				+ account.getAccountNumberLast4();
	}

	/** The profile with its documents and bank account checks, read from those records. */
	private NexHomePartnerProfileResponse toProfileDto(NexHomePartnerProfile profile) {
		Long partnerId = profile.getId();
		return partnerProfileMapper.toPartnerProfileDto(profile,
				documentsVerified(partnerDocumentRepository.findByPartnerIdIn(List.of(partnerId))),
				bankAccountVerified(partnerBankAccountRepository.findByPartnerIdAndPrimaryTrue(partnerId).orElse(null)));
	}

	/** A page of profiles; their documents and primary bank accounts are loaded in one query each. */
	private List<NexHomePartnerProfileResponse> toProfileDtos(List<NexHomePartnerProfile> profiles) {
		if (profiles.isEmpty()) {
			return List.of();
		}
		List<Long> partnerIds = profiles.stream().map(NexHomePartnerProfile::getId).toList();
		Map<Long, List<NexHomePartnerDocument>> documents = partnerDocumentRepository.findByPartnerIdIn(partnerIds)
				.stream()
				.collect(Collectors.groupingBy(document -> document.getPartner().getId()));
		Map<Long, NexHomePartnerBankAccount> primaryAccounts = partnerBankAccountRepository
				.findByPartnerIdInAndPrimaryTrue(partnerIds).stream()
				.collect(Collectors.toMap(account -> account.getPartner().getId(), account -> account));
		return profiles.stream()
				.map(profile -> partnerProfileMapper.toPartnerProfileDto(profile,
						documentsVerified(documents.getOrDefault(profile.getId(), List.of())),
						bankAccountVerified(primaryAccounts.get(profile.getId()))))
				.toList();
	}

	/** The documents check: at least one government ID, and every document VERIFIED. */
	static boolean documentsVerified(List<NexHomePartnerDocument> documents) {
		return documents.stream().anyMatch(document -> DocumentType.GOVERNMENT_ID.equals(document.getDocumentType()))
				&& documents.stream()
						.allMatch(document -> VerificationStatus.VERIFIED.equals(document.getVerificationStatus()));
	}

	/** The bank account check: the primary account (where payouts go) is VERIFIED. */
	static boolean bankAccountVerified(NexHomePartnerBankAccount primaryAccount) {
		return primaryAccount != null && VerificationStatus.VERIFIED.equals(primaryAccount.getVerificationStatus());
	}

	// ------------------------------------------------------------------------------------------------------------
	// Documents
	// ------------------------------------------------------------------------------------------------------------

	@Override
	@Transactional
	public NexHomePartnerDocumentResponse addPartnerDocument(NexHomePartnerDocumentCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomePartnerProfile partner = findPartnerProfile(request.partnerProfileId());
		requireCanChange(partner, NexHomeAccess.WRITE);
		String documentType = lookupService.validateCode(Types.DOCUMENT_TYPE, request.documentType(),
				"Document type");
		String idType = request.idType() == null || request.idType().isBlank() ? null
				: lookupService.validateCode(Types.GOVERNMENT_ID_TYPE, request.idType(), "ID type");
		if (DocumentType.GOVERNMENT_ID.equals(documentType) && idType == null) {
			throw new InvalidRequestException("ID type is required for a government ID.");
		}
		if (DocumentType.CERTIFICATION.equals(documentType)
				&& NexHomeText.trimToNull(request.documentName()) == null) {
			throw new InvalidRequestException("Certificate name is required for a skill certificate.");
		}

		NexHomePartnerDocument document = partnerDocumentMapper.toPartnerDocumentEntity(request, partner, documentType,
				actingUserId);
		document.setIdType(idType);
		document = partnerDocumentRepository.save(document);

		log.info("Document {} ({}) added to partner profile {}", document.getId(), documentType,
				partner.getPublicId());
		return partnerDocumentMapper.toPartnerDocumentDto(document);
	}

	@Override
	@Transactional
	public NexHomePartnerDocumentResponse updatePartnerDocument(NexHomePartnerDocumentUpdateRequest request) {
		NexHomePartnerDocument document = findPartnerDocument(request.partnerDocumentId());
		requireCanChange(document.getPartner(), NexHomeAccess.WRITE);
		// Staff may correct a verified document (it goes back to PENDING and needs verifying again); the partner may not
		if (VerificationStatus.VERIFIED.equals(document.getVerificationStatus())
				&& document.getPartner().getUser().getId().equals(permissionService.currentUserId())) {
			throw new InvalidRequestException("A verified document cannot be changed; add a new document instead.");
		}
		String idType = request.idType() == null || request.idType().isBlank() ? null
				: lookupService.validateCode(Types.GOVERNMENT_ID_TYPE, request.idType(), "ID type");

		partnerDocumentMapper.updatePartnerDocumentEntity(document, request, permissionService.currentUserId());
		if (idType != null) {
			document.setIdType(idType);
		}

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

		auditRecorder.record("DOCUMENT_" + target, "PARTNER_PROFILE", document.getPartner().getPublicId(), null,
				Map.of("documentId", document.getId(), "documentType", document.getDocumentType()), actingUserId);
		if (VerificationStatus.REJECTED.equals(target)) {
			notifier.notify(document.getPartner().getUser().getId(), new NexHomeNotifier.Message(
					NotificationType.ONBOARDING, "Document rejected", "A document was rejected: " + reason,
					NexHomeNotifier.PARTNER_PROFILE, document.getPartner().getId()), actingUserId);
		}
		log.info("Document {} set to {} by User ID {}", document.getId(), target, actingUserId);
		return partnerDocumentMapper.toPartnerDocumentDto(document);
	}

	@Override
	@Transactional
	public void deletePartnerDocument(Long partnerDocumentId) {
		NexHomePartnerDocument document = findPartnerDocument(partnerDocumentId);
		requireCanChange(document.getPartner(), NexHomeAccess.DELETE);
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
		requireCanChange(partner, NexHomeAccess.WRITE);

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
		requireCanChange(account.getPartner(), NexHomeAccess.WRITE);

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
		String reason = NexHomeText.trimToNull(request.rejectionReason());
		if (VerificationStatus.REJECTED.equals(target) && reason == null) {
			throw new InvalidRequestException("Rejection reason is required to reject a bank account.");
		}

		account.setVerificationStatus(target);
		// ck_nexhome_partner_bank_accounts_rejection_reason: only a rejected account keeps a reason
		account.setRejectionReason(VerificationStatus.REJECTED.equals(target) ? reason : null);
		account.setUpdatedBy(actingUserId);

		auditRecorder.record("BANK_ACCOUNT_" + target, "PARTNER_PROFILE", account.getPartner().getPublicId(), null,
				Map.of("bankAccountId", account.getId()), actingUserId);
		if (VerificationStatus.REJECTED.equals(target)) {
			notifier.notify(account.getPartner().getUser().getId(), new NexHomeNotifier.Message(
					NotificationType.ONBOARDING, "Bank account rejected", "Your bank account was rejected: " + reason,
					NexHomeNotifier.PARTNER_PROFILE, account.getPartner().getId()), actingUserId);
		}
		log.info("Bank account {} set to {} by User ID {}", account.getId(), target, actingUserId);
		return partnerBankAccountMapper.toPartnerBankAccountDto(account);
	}

	@Override
	@Transactional
	public void deletePartnerBankAccount(Long partnerBankAccountId) {
		NexHomePartnerBankAccount account = findPartnerBankAccount(partnerBankAccountId);
		requireCanChange(account.getPartner(), NexHomeAccess.DELETE);
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
		requireCanChange(partner, NexHomeAccess.WRITE);
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
		requireCanChange(partner, NexHomeAccess.WRITE);
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
		requireCanChange(partnerService.getPartner(), NexHomeAccess.DELETE);
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

	/**
	 * Like the profile itself, the partner changes their services only before submitting it (NOT_STARTED, DRAFT) or
	 * while changes are requested.
	 */
	private static void requireEditableByOwner(NexHomePartnerProfile partner, Long actingUserId) {
		if (partner.getUser().getId().equals(actingUserId)
				&& !EDITABLE_BY_PARTNER.contains(partner.getOnboardingStatus())) {
			throw new InvalidRequestException("The profile is " + partner.getOnboardingStatus()
					+ " and cannot be changed now. Contact support to update it.");
		}
	}

	/** Saving a NOT_STARTED profile's fields makes it a "Profile Draft". */
	private static void startDraft(NexHomePartnerProfile profile) {
		if (PartnerOnboardingStatus.NOT_STARTED.equals(profile.getOnboardingStatus())) {
			profile.setOnboardingStatus(PartnerOnboardingStatus.DRAFT);
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

	/**
	 * Changing a partner's profile or records: the owner, or staff with PARTNER_ONBOARDING_MANAGE; once the partner is
	 * APPROVED, staff also need PARTNER_APPROVED_EDIT write (admins; not sub-admins).
	 */
	private void requireCanChange(NexHomePartnerProfile partner, NexHomeAccess access) {
		Long ownerId = partner.getUser().getId();
		requireSelfOrCan(ownerId, access);
		if (PartnerOnboardingStatus.APPROVED.equals(partner.getOnboardingStatus())
				&& !ownerId.equals(permissionService.currentUserId())
				&& !permissionService.can(PARTNER_APPROVED_EDIT, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Changing an approved partner needs " + PARTNER_APPROVED_EDIT
					+ " write access.");
		}
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
