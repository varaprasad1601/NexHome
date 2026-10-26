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
import com.nexhome.app.dto.NexHomePolicyAcceptanceCreateRequest;
import com.nexhome.app.dto.NexHomePolicyAcceptanceResponse;
import com.nexhome.app.dto.NexHomePolicyDocumentCreateRequest;
import com.nexhome.app.dto.NexHomePolicyDocumentPublishRequest;
import com.nexhome.app.dto.NexHomePolicyDocumentResponse;
import com.nexhome.app.dto.NexHomePolicyDocumentUpdateRequest;
import com.nexhome.app.service.NexHomePolicyService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Versioned policies and their acceptance. The current versions are public; accepting needs a signed-in user;
 * maintaining versions and reading other users' acceptances need POLICY_MANAGE.
 */
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class NexHomePolicyController {

	private final NexHomePolicyService policyService;

	/** The current version of every policy; optional {@code audience} (CUSTOMER or PARTNER also return BOTH). */
	@GetMapping("/current")
	public List<NexHomePolicyDocumentResponse> getCurrentPolicies(@RequestParam(required = false) String audience) {
		return policyService.getCurrentPolicies(audience);
	}

	@GetMapping("/current/{policyKey}")
	public NexHomePolicyDocumentResponse getCurrentPolicy(@PathVariable String policyKey) {
		return policyService.getCurrentPolicy(policyKey);
	}

	/** The current policies for the caller's roles that they have not accepted yet. */
	@GetMapping("/pending")
	public List<NexHomePolicyDocumentResponse> getPendingPolicies() {
		return policyService.getPendingPolicies();
	}

	/** Accepts the current version of a policy; the client address is recorded. */
	@PostMapping("/accept")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomePolicyAcceptanceResponse acceptPolicy(@Valid @RequestBody NexHomePolicyAcceptanceCreateRequest request,
			HttpServletRequest servletRequest) {
		return policyService.acceptPolicy(request, servletRequest.getRemoteAddr());
	}

	@GetMapping("/acceptances/me")
	public List<NexHomePolicyAcceptanceResponse> getCurrentUserAcceptances() {
		return policyService.getCurrentUserAcceptances();
	}

	@GetMapping("/acceptances/users/{userId}")
	@PreAuthorize("@permissions.isSelfOrCan(#userId, 'POLICY_MANAGE', 'READ')")
	public List<NexHomePolicyAcceptanceResponse> getAcceptances(@PathVariable Long userId) {
		return policyService.getAcceptances(userId);
	}

	/**
	 * Every version, drafts included, newest first. Query parameters (all optional): {@code page}, {@code size},
	 * {@code policyKey}, {@code audience}, {@code isPublished}.
	 */
	@GetMapping("/all")
	@PreAuthorize("@permissions.can('POLICY_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomePolicyDocumentResponse> getPolicies(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String policyKey,
			@RequestParam(required = false) String audience, @RequestParam(required = false) Boolean isPublished) {
		return policyService.getPolicies(page, size, policyKey, audience, isPublished);
	}

	@GetMapping("/{policyDocumentId}")
	@PreAuthorize("@permissions.can('POLICY_MANAGE', 'READ')")
	public NexHomePolicyDocumentResponse getPolicy(@PathVariable Long policyDocumentId) {
		return policyService.getPolicy(policyDocumentId);
	}

	@PostMapping("/create")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("@permissions.can('POLICY_MANAGE', 'WRITE')")
	public NexHomePolicyDocumentResponse createPolicy(@Valid @RequestBody NexHomePolicyDocumentCreateRequest request) {
		return policyService.createPolicy(request);
	}

	@PutMapping("/update")
	@PreAuthorize("@permissions.can('POLICY_MANAGE', 'WRITE')")
	public NexHomePolicyDocumentResponse updatePolicy(@Valid @RequestBody NexHomePolicyDocumentUpdateRequest request) {
		return policyService.updatePolicy(request);
	}

	/** Publishing cannot be undone. */
	@PutMapping("/publish")
	@PreAuthorize("@permissions.can('POLICY_MANAGE', 'WRITE')")
	public NexHomePolicyDocumentResponse publishPolicy(
			@Valid @RequestBody NexHomePolicyDocumentPublishRequest request) {
		return policyService.publishPolicy(request);
	}

	@DeleteMapping("/delete/{policyDocumentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("@permissions.can('POLICY_MANAGE', 'DELETE')")
	public void deletePolicy(@PathVariable Long policyDocumentId) {
		policyService.deletePolicy(policyDocumentId);
	}
}
