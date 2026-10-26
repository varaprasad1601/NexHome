package com.nexhome.app.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeAdminProfileCreateRequest;
import com.nexhome.app.dto.NexHomeAdminProfileResponse;
import com.nexhome.app.dto.NexHomeAdminProfileUpdateRequest;
import com.nexhome.app.dto.NexHomeCustomerProfileCreateRequest;
import com.nexhome.app.dto.NexHomeCustomerProfileResponse;
import com.nexhome.app.dto.NexHomeCustomerProfileUpdateRequest;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeAdminProfileMapper;
import com.nexhome.app.mapper.NexHomeCustomerProfileMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeAdminProfile;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAdminProfileRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeProfileServiceImpl implements NexHomeProfileService {

	static final String PROFILE_MANAGE = "PROFILE_MANAGE";
	static final String STAFF_MANAGE = "STAFF_MANAGE";
	private static final List<String> STAFF_ROLES = List.of(NexHomeRole.SUPER_USER, NexHomeRole.ADMIN,
			NexHomeRole.SUB_ADMIN);

	private final NexHomeCustomerProfileRepository customerProfileRepository;
	private final NexHomeAdminProfileRepository adminProfileRepository;
	private final NexHomeUserRepository userRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeCustomerProfileMapper customerProfileMapper;
	private final NexHomeAdminProfileMapper adminProfileMapper;

	@Override
	@Transactional
	public NexHomeCustomerProfileResponse createCustomerProfile(NexHomeCustomerProfileCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeUser user = findUser(request.userId());
		if (!userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(user.getId(),
				NexHomeRole.CUSTOMER)) {
			throw new InvalidRequestException("Customer profiles are only for accounts with the CUSTOMER role.");
		}
		if (customerProfileRepository.existsByUserId(user.getId())) {
			throw new DuplicateResourceException("This account already has a customer profile.");
		}

		NexHomeCustomerProfile profile = customerProfileMapper.toCustomerProfileEntity(request, user, actingUserId);
		profile.setPreferredLanguage(language(request.preferredLanguage()));
		profile = customerProfileRepository.save(profile);

		log.info("Customer profile {} created for User ID {}", profile.getPublicId(), user.getId());
		return customerProfileMapper.toCustomerProfileDto(profile);
	}

	@Override
	@Transactional
	public NexHomeCustomerProfileResponse updateCustomerProfile(NexHomeCustomerProfileUpdateRequest request) {
		NexHomeCustomerProfile profile = findCustomerProfile(request.customerProfileId());
		requireSelfOrCan(profile.getUser().getId(), PROFILE_MANAGE, NexHomeAccess.WRITE);

		customerProfileMapper.updateCustomerProfileEntity(profile, request, permissionService.currentUserId());
		if (request.preferredLanguage() != null) {
			profile.setPreferredLanguage(language(request.preferredLanguage()));
		}

		log.info("Customer profile {} updated", profile.getPublicId());
		return customerProfileMapper.toCustomerProfileDto(profile);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeCustomerProfileResponse getCustomerProfile(Long customerProfileId) {
		NexHomeCustomerProfile profile = findCustomerProfile(customerProfileId);
		requireSelfOrCan(profile.getUser().getId(), PROFILE_MANAGE, NexHomeAccess.READ);
		return customerProfileMapper.toCustomerProfileDto(profile);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeCustomerProfileResponse getCurrentCustomerProfile() {
		return customerProfileRepository.findByUserId(permissionService.currentUserId())
				.map(customerProfileMapper::toCustomerProfileDto)
				.orElseThrow(() -> new ResourceNotFoundException("You do not have a customer profile yet."));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeCustomerProfileResponse> getCustomerProfiles(Integer page, Integer size,
			String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomeCustomerProfile> profiles = customerProfileRepository.search(NexHomePaging.searchText(search),
				pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, profiles,
				profiles.stream().map(customerProfileMapper::toCustomerProfileDto).toList());
	}

	@Override
	@Transactional
	public NexHomeAdminProfileResponse createAdminProfile(NexHomeAdminProfileCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeUser user = findUser(request.userId());
		if (!userRoleRepository.existsByUserIdAndRoleCodeInAndActiveTrueAndRoleActiveFlagTrue(user.getId(),
				STAFF_ROLES)) {
			throw new InvalidRequestException("Staff profiles are only for super users, admins and sub-admins.");
		}
		requireStaffManagerFor(user.getId(), actingUserId, request.employeeCode() != null
				|| request.department() != null || request.jobTitle() != null);
		if (adminProfileRepository.existsByUserId(user.getId())) {
			throw new DuplicateResourceException("This account already has a staff profile.");
		}

		NexHomeAdminProfile profile = adminProfileMapper.toAdminProfileEntity(request, user, actingUserId);
		requireUniqueEmployeeCode(profile.getEmployeeCode(), null);
		requirePostalCodeForCountry(profile.getCountryCode(), profile.getPostalCode());
		profile = adminProfileRepository.save(profile);

		log.info("Staff profile {} created for User ID {}", profile.getPublicId(), user.getId());
		return adminProfileMapper.toAdminProfileDto(profile);
	}

	@Override
	@Transactional
	public NexHomeAdminProfileResponse updateAdminProfile(NexHomeAdminProfileUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeAdminProfile profile = findAdminProfile(request.adminProfileId());
		Long ownerId = profile.getUser().getId();
		requireSelfOrCan(ownerId, STAFF_MANAGE, NexHomeAccess.WRITE);
		requireStaffManagerFor(ownerId, actingUserId, request.employeeCode() != null || request.department() != null
				|| request.jobTitle() != null);

		// Checked before the entity changes: the query would otherwise flush the change into the unique index first
		if (request.employeeCode() != null) {
			requireUniqueEmployeeCode(NexHomeText.upperCase(request.employeeCode()), profile.getId());
		}
		adminProfileMapper.updateAdminProfileEntity(profile, request, actingUserId);
		requirePostalCodeForCountry(profile.getCountryCode(), profile.getPostalCode());

		log.info("Staff profile {} updated", profile.getPublicId());
		return adminProfileMapper.toAdminProfileDto(profile);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeAdminProfileResponse getAdminProfile(Long adminProfileId) {
		NexHomeAdminProfile profile = findAdminProfile(adminProfileId);
		requireSelfOrCan(profile.getUser().getId(), STAFF_MANAGE, NexHomeAccess.READ);
		return adminProfileMapper.toAdminProfileDto(profile);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeAdminProfileResponse getCurrentAdminProfile() {
		return adminProfileRepository.findByUserId(permissionService.currentUserId())
				.map(adminProfileMapper::toAdminProfileDto)
				.orElseThrow(() -> new ResourceNotFoundException("You do not have a staff profile yet."));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeAdminProfileResponse> getAdminProfiles(Integer page, Integer size,
			String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		Page<NexHomeAdminProfile> profiles = adminProfileRepository.search(NexHomePaging.searchText(search),
				pageRequest);
		return NexHomePaging.toPageResponse(pageRequest, profiles,
				profiles.stream().map(adminProfileMapper::toAdminProfileDto).toList());
	}

	/**
	 * Staff may maintain their own names and address. Employee details, and anyone else's profile, need STAFF_MANAGE
	 * write; a super user's profile can only be changed by a super user (other than by its owner).
	 */
	private void requireStaffManagerFor(Long ownerId, Long actingUserId, boolean employeeDetails) {
		boolean self = ownerId.equals(actingUserId);
		if ((!self || employeeDetails) && !permissionService.can(STAFF_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("Employee details need STAFF_MANAGE write.");
		}
		if (!self && permissionService.isSuperUser(ownerId) && !permissionService.isSuperUser(actingUserId)) {
			throw new AccessDeniedException("Only a super user can change a super user's staff profile.");
		}
	}

	private void requireUniqueEmployeeCode(String employeeCode, Long adminProfileId) {
		if (employeeCode == null) {
			return;
		}
		adminProfileRepository.findByEmployeeCode(employeeCode)
				.filter(existing -> !existing.getId().equals(adminProfileId))
				.ifPresent(existing -> {
					throw new DuplicateResourceException("Another staff profile has this employee code.");
				});
	}

	private void requireSelfOrCan(Long ownerId, String functionCode, NexHomeAccess access) {
		if (!permissionService.isSelfOrCan(ownerId, functionCode, access.name())) {
			throw new AccessDeniedException("Not the owner and no " + functionCode + " " + access + " access.");
		}
	}

	/** An empty code clears the language. */
	private String language(String code) {
		return NexHomeText.trimToNull(code) == null ? null
				: lookupService.validateCode(Types.PREFERRED_CONTACT_LANGUAGE, code, "Preferred language");
	}

	/** Indian addresses need a 6-digit PIN code; other countries keep the general format checked by the request. */
	static void requirePostalCodeForCountry(String countryCode, String postalCode) {
		if ("IN".equals(countryCode) && !postalCode.matches("^[1-9][0-9]{5}$")) {
			throw new InvalidRequestException("Postal code must be a 6-digit PIN code for India.");
		}
	}

	private NexHomeUser findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));
	}

	private NexHomeCustomerProfile findCustomerProfile(Long customerProfileId) {
		return customerProfileRepository.findById(customerProfileId)
				.orElseThrow(() -> new ResourceNotFoundException("Customer profile does not exist."));
	}

	private NexHomeAdminProfile findAdminProfile(Long adminProfileId) {
		return adminProfileRepository.findById(adminProfileId)
				.orElseThrow(() -> new ResourceNotFoundException("Staff profile does not exist."));
	}
}
