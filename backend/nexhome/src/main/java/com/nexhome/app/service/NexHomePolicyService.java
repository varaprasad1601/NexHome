package com.nexhome.app.service;

import java.util.List;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePolicyAcceptanceCreateRequest;
import com.nexhome.app.dto.NexHomePolicyAcceptanceResponse;
import com.nexhome.app.dto.NexHomePolicyDocumentCreateRequest;
import com.nexhome.app.dto.NexHomePolicyDocumentPublishRequest;
import com.nexhome.app.dto.NexHomePolicyDocumentResponse;
import com.nexhome.app.dto.NexHomePolicyDocumentUpdateRequest;

/**
 * Versioned policies (terms, privacy, cancellation, ...) and users' acceptance of them. Each version is its own
 * document; once published it never changes. The current version of a policy is its published version with the latest
 * {@code effectiveFrom} that has started.
 */
public interface NexHomePolicyService {

	/**
	 * The current version of every policy, by key.
	 *
	 * @param audience optional POLICY_AUDIENCE code; CUSTOMER or PARTNER also include BOTH
	 */
	List<NexHomePolicyDocumentResponse> getCurrentPolicies(String audience);

	/** The current version of one policy. */
	NexHomePolicyDocumentResponse getCurrentPolicy(String policyKey);

	/** One page of all versions, drafts included, newest first. */
	NexHomePageResponse<NexHomePolicyDocumentResponse> getPolicies(Integer page, Integer size, String policyKey,
			String audience, Boolean isPublished);

	NexHomePolicyDocumentResponse getPolicy(Long policyDocumentId);

	NexHomePolicyDocumentResponse createPolicy(NexHomePolicyDocumentCreateRequest request);

	/** Unpublished versions only. */
	NexHomePolicyDocumentResponse updatePolicy(NexHomePolicyDocumentUpdateRequest request);

	/** Publishes a version; it then never changes and cannot be deleted. */
	NexHomePolicyDocumentResponse publishPolicy(NexHomePolicyDocumentPublishRequest request);

	/** Unpublished versions only. */
	void deletePolicy(Long policyDocumentId);

	/**
	 * Records that the caller accepts a policy version, which must be the current one; once per user and version.
	 *
	 * @param ipAddress the client's address as seen by the server, or null
	 */
	NexHomePolicyAcceptanceResponse acceptPolicy(NexHomePolicyAcceptanceCreateRequest request, String ipAddress);

	/**
	 * The current policies the caller has not accepted yet, for their roles: CUSTOMER and/or PARTNER policies plus
	 * BOTH. Accounts with neither role have none.
	 */
	List<NexHomePolicyDocumentResponse> getPendingPolicies();

	/** A user's acceptances, newest first. */
	List<NexHomePolicyAcceptanceResponse> getAcceptances(Long userId);

	/** The caller's acceptances, newest first. */
	List<NexHomePolicyAcceptanceResponse> getCurrentUserAcceptances();
}
