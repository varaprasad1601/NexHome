package com.nexhome.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomePageResponse;
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
import com.nexhome.app.dto.NexHomePartnerProfileResponse;
import com.nexhome.app.dto.NexHomePartnerProfileReviewRequest;
import com.nexhome.app.dto.NexHomePartnerProfileSubmitRequest;
import com.nexhome.app.dto.NexHomePartnerProfileUpdateRequest;
import com.nexhome.app.dto.NexHomePartnerProfileVerificationRequest;
import com.nexhome.app.dto.NexHomePartnerServiceCreateRequest;
import com.nexhome.app.dto.NexHomePartnerServiceResponse;
import com.nexhome.app.dto.NexHomePartnerServiceUpdateRequest;
import com.nexhome.app.service.NexHomePartnerOnboardingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Partner onboarding: profiles and their review, services offered, documents and bank accounts. Partners manage their own records;
 * other partners' records, reviews and verification need PARTNER_ONBOARDING_MANAGE. Endpoints that take a record id
 * check the owner in the service, after loading the record.
 */
@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
public class NexHomePartnerController {

	private final NexHomePartnerOnboardingService partnerService;

	@PostMapping("/profiles/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.isSelfOrCan(#request.userId(), 'PARTNER_ONBOARDING_MANAGE', 'WRITE')")
	public NexHomePartnerProfileResponse createPartnerProfile(
			@Valid @RequestBody NexHomePartnerProfileCreateRequest request) {
		return partnerService.createPartnerProfile(request);
	}

	/** Owner (while NOT_STARTED, DRAFT or CHANGES_REQUESTED) or PARTNER_ONBOARDING_MANAGE write. */
	@PutMapping("/profiles/update")
	public NexHomePartnerProfileResponse updatePartnerProfile(
			@Valid @RequestBody NexHomePartnerProfileUpdateRequest request) {
		return partnerService.updatePartnerProfile(request);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE write: sends a complete profile for review. */
	@PutMapping("/profiles/submit")
	public NexHomePartnerProfileResponse submitPartnerProfile(
			@Valid @RequestBody NexHomePartnerProfileSubmitRequest request) {
		return partnerService.submitPartnerProfile(request);
	}

	/** Approve, reject or send back for changes. Never for your own profile. */
	@PutMapping("/profiles/review")
	@PreAuthorize("@permissions.can('PARTNER_APPROVE', 'WRITE')")
	public NexHomePartnerProfileResponse reviewPartnerProfile(
			@Valid @RequestBody NexHomePartnerProfileReviewRequest request) {
		return partnerService.reviewPartnerProfile(request);
	}

	/** The account, documents and bank account checks, each with who made it. Never for your own profile. */
	@PutMapping("/profiles/verifications/update")
	@PreAuthorize("@permissions.can('PARTNER_VERIFY', 'WRITE')")
	public NexHomePartnerProfileResponse updatePartnerVerifications(
			@Valid @RequestBody NexHomePartnerProfileVerificationRequest request) {
		return partnerService.updatePartnerVerifications(request);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE write; approved partners only. */
	@PutMapping("/profiles/availability/update")
	public NexHomePartnerProfileResponse updatePartnerAvailability(
			@Valid @RequestBody NexHomePartnerAvailabilityUpdateRequest request) {
		return partnerService.updatePartnerAvailability(request);
	}

	@GetMapping("/profiles/me")
	public NexHomePartnerProfileResponse getCurrentPartnerProfile() {
		return partnerService.getCurrentPartnerProfile();
	}

	/**
	 * Partner profiles, newest first. Query parameters (all optional): {@code page} (0-based), {@code size} (1-100,
	 * default 10), {@code onboardingStatus}, {@code approvalStatus}, {@code availabilityStatus}, {@code city}
	 * (exact, case-insensitive), {@code search} (part of a name or the public id).
	 */
	@GetMapping("/profiles")
	@PreAuthorize("@permissions.can('PARTNER_ONBOARDING_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomePartnerProfileResponse> getPartnerProfiles(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String onboardingStatus,
			@RequestParam(required = false) String approvalStatus,
			@RequestParam(required = false) String availabilityStatus, @RequestParam(required = false) String city,
			@RequestParam(required = false) String search) {
		return partnerService.getPartnerProfiles(page, size, onboardingStatus, approvalStatus, availabilityStatus,
				city, search);
	}

	/** The onboarding audit trail: who created, changed, verified and approved what, and when. */
	@GetMapping("/profiles/audit/{partnerProfileId}")
	@PreAuthorize("@permissions.can('PARTNER_ONBOARDING_MANAGE', 'READ')")
	public List<NexHomeAuditEntry> getPartnerAudit(@PathVariable Long partnerProfileId) {
		return partnerService.getPartnerAudit(partnerProfileId);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE read. */
	@GetMapping("/profiles/{partnerProfileId}")
	public NexHomePartnerProfileResponse getPartnerProfile(@PathVariable Long partnerProfileId) {
		return partnerService.getPartnerProfile(partnerProfileId);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE read. */
	@GetMapping("/profiles/documents/{partnerProfileId}")
	public List<NexHomePartnerDocumentResponse> getPartnerDocuments(@PathVariable Long partnerProfileId) {
		return partnerService.getPartnerDocuments(partnerProfileId);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE read. */
	@GetMapping("/profiles/bank-accounts/{partnerProfileId}")
	public List<NexHomePartnerBankAccountResponse> getPartnerBankAccounts(@PathVariable Long partnerProfileId) {
		return partnerService.getPartnerBankAccounts(partnerProfileId);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE read. */
	@GetMapping("/profiles/services/{partnerProfileId}")
	public List<NexHomePartnerServiceResponse> getPartnerServices(@PathVariable Long partnerProfileId) {
		return partnerService.getPartnerServices(partnerProfileId);
	}

	/** Owner (while NOT_STARTED, DRAFT or CHANGES_REQUESTED) or PARTNER_ONBOARDING_MANAGE write. */
	@PostMapping("/services/add")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomePartnerServiceResponse addPartnerService(
			@Valid @RequestBody NexHomePartnerServiceCreateRequest request) {
		return partnerService.addPartnerService(request);
	}

	/** Owner (while NOT_STARTED, DRAFT or CHANGES_REQUESTED) or PARTNER_ONBOARDING_MANAGE write. */
	@PutMapping("/services/update")
	public NexHomePartnerServiceResponse updatePartnerService(
			@Valid @RequestBody NexHomePartnerServiceUpdateRequest request) {
		return partnerService.updatePartnerService(request);
	}

	/** Owner (while NOT_STARTED, DRAFT or CHANGES_REQUESTED) or PARTNER_ONBOARDING_MANAGE delete. */
	@DeleteMapping("/services/delete/{partnerServiceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deletePartnerService(@PathVariable Long partnerServiceId) {
		partnerService.deletePartnerService(partnerServiceId);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE write. */
	@PostMapping("/documents/add")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomePartnerDocumentResponse addPartnerDocument(
			@Valid @RequestBody NexHomePartnerDocumentCreateRequest request) {
		return partnerService.addPartnerDocument(request);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE write. */
	@PutMapping("/documents/update")
	public NexHomePartnerDocumentResponse updatePartnerDocument(
			@Valid @RequestBody NexHomePartnerDocumentUpdateRequest request) {
		return partnerService.updatePartnerDocument(request);
	}

	/** Never for your own documents. */
	@PutMapping("/documents/verify")
	@PreAuthorize("@permissions.can('PARTNER_VERIFY', 'WRITE')")
	public NexHomePartnerDocumentResponse verifyPartnerDocument(
			@Valid @RequestBody NexHomePartnerDocumentVerifyRequest request) {
		return partnerService.verifyPartnerDocument(request);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE delete. */
	@DeleteMapping("/documents/delete/{partnerDocumentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deletePartnerDocument(@PathVariable Long partnerDocumentId) {
		partnerService.deletePartnerDocument(partnerDocumentId);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE write. */
	@PostMapping("/bank-accounts/add")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomePartnerBankAccountResponse addPartnerBankAccount(
			@Valid @RequestBody NexHomePartnerBankAccountCreateRequest request) {
		return partnerService.addPartnerBankAccount(request);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE write. */
	@PutMapping("/bank-accounts/update")
	public NexHomePartnerBankAccountResponse updatePartnerBankAccount(
			@Valid @RequestBody NexHomePartnerBankAccountUpdateRequest request) {
		return partnerService.updatePartnerBankAccount(request);
	}

	/** Never for your own bank accounts. */
	@PutMapping("/bank-accounts/verify")
	@PreAuthorize("@permissions.can('PARTNER_VERIFY', 'WRITE')")
	public NexHomePartnerBankAccountResponse verifyPartnerBankAccount(
			@Valid @RequestBody NexHomePartnerBankAccountVerifyRequest request) {
		return partnerService.verifyPartnerBankAccount(request);
	}

	/** Owner or PARTNER_ONBOARDING_MANAGE delete. */
	@DeleteMapping("/bank-accounts/delete/{partnerBankAccountId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deletePartnerBankAccount(@PathVariable Long partnerBankAccountId) {
		partnerService.deletePartnerBankAccount(partnerBankAccountId);
	}
}
