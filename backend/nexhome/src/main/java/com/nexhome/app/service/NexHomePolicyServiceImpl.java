package com.nexhome.app.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomePageResponse;
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
import com.nexhome.app.model.NexHomeLookupCodes.Audience;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomePolicyAcceptance;
import com.nexhome.app.model.NexHomePolicyDocument;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomePolicyAcceptanceRepository;
import com.nexhome.app.repository.NexHomePolicyDocumentRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomePolicyServiceImpl implements NexHomePolicyService {

	private final NexHomePolicyDocumentRepository policyRepository;
	private final NexHomePolicyAcceptanceRepository acceptanceRepository;
	private final NexHomeUserRepository userRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomePolicyDocumentMapper policyMapper;
	private final NexHomePolicyAcceptanceMapper acceptanceMapper;

	@Override
	@Transactional(readOnly = true)
	public List<NexHomePolicyDocumentResponse> getCurrentPolicies(String audience) {
		Set<String> audiences = null;
		if (audience != null && !audience.isBlank()) {
			String code = lookupService.validateCode(Types.POLICY_AUDIENCE, audience, "Audience");
			audiences = Audience.BOTH.equals(code) ? Set.of(Audience.BOTH) : Set.of(code, Audience.BOTH);
		}
		Set<String> wanted = audiences;
		return currentVersions().values().stream()
				.filter(policy -> wanted == null || wanted.contains(policy.getAudience()))
				.map(policyMapper::toPolicyDocumentDto)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePolicyDocumentResponse getCurrentPolicy(String policyKey) {
		NexHomePolicyDocument policy = currentVersions().get(policyKey);
		if (policy == null) {
			throw new ResourceNotFoundException("No published version of this policy is in effect.");
		}
		return policyMapper.toPolicyDocumentDto(policy);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomePolicyDocumentResponse> getPolicies(Integer page, Integer size,
			String policyKey, String audience, Boolean isPublished) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		String audienceCode = audience == null || audience.isBlank() ? null
				: lookupService.validateCode(Types.POLICY_AUDIENCE, audience, "Audience");
		Page<NexHomePolicyDocument> policies = policyRepository.search(
				policyKey == null || policyKey.isBlank() ? null : policyKey.trim(), audienceCode, isPublished,
				pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, policies,
				policies.stream().map(policyMapper::toPolicyDocumentDto).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePolicyDocumentResponse getPolicy(Long policyDocumentId) {
		return policyMapper.toPolicyDocumentDto(findPolicy(policyDocumentId));
	}

	@Override
	@Transactional
	public NexHomePolicyDocumentResponse createPolicy(NexHomePolicyDocumentCreateRequest request) {
		String audience = lookupService.validateCode(Types.POLICY_AUDIENCE, request.audience(), "Audience");
		if (policyRepository.existsByPolicyKeyAndVersion(request.policyKey().trim(), request.version().trim())) {
			throw new DuplicateResourceException("This version of the policy already exists");
		}
		NexHomePolicyDocument policy = policyMapper.toPolicyDocumentEntity(request, permissionService.currentUserId());
		policy.setAudience(audience);
		policy = policyRepository.save(policy);

		log.info("Policy {} version {} created", policy.getPolicyKey(), policy.getVersion());
		return policyMapper.toPolicyDocumentDto(policy);
	}

	@Override
	@Transactional
	public NexHomePolicyDocumentResponse updatePolicy(NexHomePolicyDocumentUpdateRequest request) {
		NexHomePolicyDocument policy = findPolicy(request.policyDocumentId());
		if (policy.isPublished()) {
			throw new InvalidRequestException("A published policy cannot be changed; create a new version instead.");
		}
		String audience = request.audience() == null ? null
				: lookupService.validateCode(Types.POLICY_AUDIENCE, request.audience(), "Audience");

		policyMapper.updatePolicyDocumentEntity(policy, request, permissionService.currentUserId());
		if (audience != null) {
			policy.setAudience(audience);
		}

		log.info("Policy {} version {} updated", policy.getPolicyKey(), policy.getVersion());
		return policyMapper.toPolicyDocumentDto(policy);
	}

	@Override
	@Transactional
	public NexHomePolicyDocumentResponse publishPolicy(NexHomePolicyDocumentPublishRequest request) {
		NexHomePolicyDocument policy = findPolicy(request.policyDocumentId());
		if (policy.isPublished()) {
			throw new InvalidRequestException("Policy is already published.");
		}
		policy.setPublished(true);
		policy.setPublishedAt(Instant.now());
		policy.setUpdatedBy(permissionService.currentUserId());

		log.info("Policy {} version {} published", policy.getPolicyKey(), policy.getVersion());
		return policyMapper.toPolicyDocumentDto(policy);
	}

	@Override
	@Transactional
	public void deletePolicy(Long policyDocumentId) {
		NexHomePolicyDocument policy = findPolicy(policyDocumentId);
		if (policy.isPublished()) {
			throw new InvalidRequestException("A published policy cannot be deleted.");
		}
		policyRepository.delete(policy);
		log.info("Policy {} version {} deleted", policy.getPolicyKey(), policy.getVersion());
	}

	@Override
	@Transactional
	public NexHomePolicyAcceptanceResponse acceptPolicy(NexHomePolicyAcceptanceCreateRequest request,
			String ipAddress) {
		NexHomeUser user = userRepository.findById(permissionService.currentUserId())
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));
		NexHomePolicyDocument policy = findPolicy(request.policyDocumentId());
		NexHomePolicyDocument current = currentVersions().get(policy.getPolicyKey());
		if (current == null || !current.getId().equals(policy.getId())) {
			throw new InvalidRequestException("Only the current version of a policy can be accepted.");
		}
		String method = lookupService.validateCode(Types.ACCEPTANCE_METHOD, request.acceptanceMethod(),
				"Acceptance method");
		if (acceptanceRepository.existsByUserIdAndPolicyId(user.getId(), policy.getId())) {
			throw new DuplicateResourceException("You have already accepted this policy version.");
		}

		NexHomePolicyAcceptance acceptance = acceptanceRepository.save(
				acceptanceMapper.toPolicyAcceptanceEntity(user, policy, method, parseAddress(ipAddress)));

		log.info("User ID {} accepted policy {} version {}", user.getId(), policy.getPolicyKey(), policy.getVersion());
		return acceptanceMapper.toPolicyAcceptanceDto(acceptance);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomePolicyDocumentResponse> getPendingPolicies() {
		Long userId = permissionService.currentUserId();
		List<String> audiences = new ArrayList<>();
		if (userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(userId,
				NexHomeRole.CUSTOMER)) {
			audiences.add(Audience.CUSTOMER);
		}
		if (userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(userId,
				NexHomeRole.PARTNER)) {
			audiences.add(Audience.PARTNER);
		}
		if (audiences.isEmpty()) {
			return List.of();
		}
		audiences.add(Audience.BOTH);

		List<NexHomePolicyDocument> current = currentVersions().values().stream()
				.filter(policy -> audiences.contains(policy.getAudience()))
				.toList();
		if (current.isEmpty()) {
			return List.of();
		}
		Set<Long> accepted = acceptanceRepository.findByUserIdAndPolicyIdIn(userId,
				current.stream().map(NexHomePolicyDocument::getId).toList()).stream()
				.map(acceptance -> acceptance.getPolicy().getId())
				.collect(Collectors.toSet());
		return current.stream()
				.filter(policy -> !accepted.contains(policy.getId()))
				.map(policyMapper::toPolicyDocumentDto)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomePolicyAcceptanceResponse> getAcceptances(Long userId) {
		if (!userRepository.existsById(userId)) {
			throw new ResourceNotFoundException("User does not exist.");
		}
		return acceptanceRepository.findByUserIdOrderByAcceptedAtDescIdDesc(userId).stream()
				.map(acceptanceMapper::toPolicyAcceptanceDto)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomePolicyAcceptanceResponse> getCurrentUserAcceptances() {
		return getAcceptances(permissionService.currentUserId());
	}

	/** Each policy key's current version: the published one with the latest effective date that has started. */
	private Map<String, NexHomePolicyDocument> currentVersions() {
		Map<String, NexHomePolicyDocument> current = new LinkedHashMap<>();
		// Ordered by key, then newest effective first: the first row of each key wins
		for (NexHomePolicyDocument policy : policyRepository.findPublishedInEffect(Instant.now())) {
			current.putIfAbsent(policy.getPolicyKey(), policy);
		}
		return current;
	}

	/**
	 * Only literal addresses: dotted IPv4, or IPv6 (which always contains a colon). Anything else, e.g. a host name
	 * made of hex letters such as "cafe", is ignored, so no name lookup ever happens.
	 */
	static InetAddress parseAddress(String address) {
		if (address == null || !(address.matches("\\d{1,3}(\\.\\d{1,3}){3}")
				|| address.contains(":") && address.matches("[0-9A-Fa-f:.]+"))) {
			return null;
		}
		try {
			return InetAddress.getByName(address);
		}
		catch (UnknownHostException ex) {
			return null;
		}
	}

	private NexHomePolicyDocument findPolicy(Long policyDocumentId) {
		return policyRepository.findById(policyDocumentId)
				.orElseThrow(() -> new ResourceNotFoundException("Policy document does not exist."));
	}
}
