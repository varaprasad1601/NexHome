package com.nexhome.app.service;

import java.util.List;

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
import com.nexhome.app.dto.NexHomePartnerProfileResponse;
import com.nexhome.app.dto.NexHomePartnerProfileReviewRequest;
import com.nexhome.app.dto.NexHomePartnerProfileSubmitRequest;
import com.nexhome.app.dto.NexHomePartnerProfileUpdateRequest;
import com.nexhome.app.dto.NexHomePartnerServiceCreateRequest;
import com.nexhome.app.dto.NexHomePartnerServiceResponse;
import com.nexhome.app.dto.NexHomePartnerServiceUpdateRequest;

/**
 * Partner onboarding: the partner profile and its review, the services offered, identity documents and payout bank
 * accounts. Partners manage their own records; acting on someone else's, reviewing and verifying need
 * PARTNER_ONBOARDING_MANAGE, and nobody can review or verify their own.
 * <p>
 * Lifecycle: DRAFT -(submit)-> UNDER_REVIEW -(review)-> APPROVED, REJECTED, PROFILE_INCOMPLETE (back to the partner,
 * who submits again) or TRAINING_REQUIRED; APPROVED &lt;-&gt; SUSPENDED; REJECTED can be reopened as
 * PROFILE_INCOMPLETE.
 */
public interface NexHomePartnerOnboardingService {

	/** Starts the DRAFT profile of an account holding the PARTNER role; one per account. */
	NexHomePartnerProfileResponse createPartnerProfile(NexHomePartnerProfileCreateRequest request);

	/**
	 * Saves profile fields. The partner can do so only while it is a DRAFT or PROFILE_INCOMPLETE, staff at any time;
	 * a submitted profile must stay complete.
	 */
	NexHomePartnerProfileResponse updatePartnerProfile(NexHomePartnerProfileUpdateRequest request);

	/** Checks the profile is complete and sends it for review (UNDER_REVIEW). */
	NexHomePartnerProfileResponse submitPartnerProfile(NexHomePartnerProfileSubmitRequest request);

	/** Staff decision: moves the profile to its next onboarding status. */
	NexHomePartnerProfileResponse reviewPartnerProfile(NexHomePartnerProfileReviewRequest request);

	/** An approved partner goes ONLINE, OFFLINE or BUSY. */
	NexHomePartnerProfileResponse updatePartnerAvailability(NexHomePartnerAvailabilityUpdateRequest request);

	/** Owner or PARTNER_ONBOARDING_MANAGE read. */
	NexHomePartnerProfileResponse getPartnerProfile(Long partnerProfileId);

	/** The caller's partner profile. */
	NexHomePartnerProfileResponse getCurrentPartnerProfile();

	/**
	 * One page of partner profiles, newest first, filtered by any of: onboarding, verification and availability
	 * status codes, city (exact, case-insensitive) and search text (part of a name or the public id).
	 */
	NexHomePageResponse<NexHomePartnerProfileResponse> getPartnerProfiles(Integer page, Integer size,
			String onboardingStatus, String verificationStatus, String availabilityStatus, String city,
			String search);

	NexHomePartnerDocumentResponse addPartnerDocument(NexHomePartnerDocumentCreateRequest request);

	/** Replaces the number, file or expiry of a document that is not VERIFIED; it goes back to PENDING. */
	NexHomePartnerDocumentResponse updatePartnerDocument(NexHomePartnerDocumentUpdateRequest request);

	/** Staff decision on a document. */
	NexHomePartnerDocumentResponse verifyPartnerDocument(NexHomePartnerDocumentVerifyRequest request);

	/** Deletes a document that is not VERIFIED. */
	void deletePartnerDocument(Long partnerDocumentId);

	/** The documents of a partner profile, newest first. */
	List<NexHomePartnerDocumentResponse> getPartnerDocuments(Long partnerProfileId);

	/** Adds a bank account; the first one (or one added with isPrimary) becomes primary. */
	NexHomePartnerBankAccountResponse addPartnerBankAccount(NexHomePartnerBankAccountCreateRequest request);

	NexHomePartnerBankAccountResponse updatePartnerBankAccount(NexHomePartnerBankAccountUpdateRequest request);

	/** Staff decision on a bank account. */
	NexHomePartnerBankAccountResponse verifyPartnerBankAccount(NexHomePartnerBankAccountVerifyRequest request);

	void deletePartnerBankAccount(Long partnerBankAccountId);

	/** The bank accounts of a partner profile, the primary first. */
	List<NexHomePartnerBankAccountResponse> getPartnerBankAccounts(Long partnerProfileId);

	/**
	 * Adds an active catalogue service the partner offers; the first one (or one added with isPrimaryService) becomes
	 * primary. Like profile fields, the partner can change their services only while the profile is a DRAFT or
	 * PROFILE_INCOMPLETE; staff at any time.
	 */
	NexHomePartnerServiceResponse addPartnerService(NexHomePartnerServiceCreateRequest request);

	NexHomePartnerServiceResponse updatePartnerService(NexHomePartnerServiceUpdateRequest request);

	void deletePartnerService(Long partnerServiceId);

	/** The services a partner offers, the primary first. */
	List<NexHomePartnerServiceResponse> getPartnerServices(Long partnerProfileId);
}
