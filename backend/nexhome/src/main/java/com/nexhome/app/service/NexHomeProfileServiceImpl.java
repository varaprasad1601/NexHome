package com.nexhome.app.service;

import java.util.ArrayList;

import java.time.Instant;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
import com.nexhome.app.dto.NexHomeAuditEntry;
import com.nexhome.app.mapper.NexHomeAdminProfileMapper;
import com.nexhome.app.mapper.NexHomeCustomerProfileMapper;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeAdminProfile;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAddressRepository;
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

	static final String CUSTOMER_MANAGE = "CUSTOMER_MANAGE";
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
	private final NexHomeAddressRepository addressRepository;
	private final NexHomeAuditUsers auditUsers;

	@Override
	@Transactional
	public NexHomeCustomerProfileResponse createCustomerProfile(NexHomeCustomerProfileCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeUser user = findUser(request.userId());
		if (!userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(user.getId(),
				NexHomeRole.CUSTOMER)) {
			throw new InvalidRequestException("Customer profiles are only for accounts with the CUSTOMER role.");
		}
		// Checked before the profile changes (a lookup query would flush a half-applied change)
		String language = language(request.preferredLanguage());

		// Every customer account has an empty profile from the start (V15): creating the profile fills it in, keeping
		// its customer id. Only a profile that already has a name is a duplicate.
		NexHomeCustomerProfile existing = customerProfileRepository.findByUserId(user.getId()).orElse(null);
		if (existing != null && existing.getFirstName() != null) {
			throw new DuplicateResourceException("This account already has a customer profile.");
		}
		NexHomeCustomerProfile profile;
		if (existing != null) {
			profile = existing;
			customerProfileMapper.fillCustomerProfileEntity(profile, request, actingUserId);
		}
		else {
			profile = customerProfileMapper.toCustomerProfileEntity(request, user, actingUserId);
		}
		profile.setPreferredLanguage(language);
		if (existing == null) {
			profile = customerProfileRepository.save(profile);
		}

		log.info("Customer profile {} filled in for User ID {}", profile.getPublicId(), user.getId());
		return customerProfileMapper.toCustomerProfileDto(profile);
	}

	@Override
	@Transactional
	public NexHomeCustomerProfileResponse updateCustomerProfile(NexHomeCustomerProfileUpdateRequest request) {
		NexHomeCustomerProfile profile = findCustomerProfile(request.customerProfileId());
		requireSelfOrCan(profile.getUser().getId(), CUSTOMER_MANAGE, NexHomeAccess.WRITE);

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
		requireSelfOrCan(profile.getUser().getId(), CUSTOMER_MANAGE, NexHomeAccess.READ);
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
		requireStaffManagerFor(ownerId, actingUserId, request.employeeCode() != null || request.department() != null
				|| request.jobTitle() != null);

		// Checked before the entity changes: the query would otherwise flush the change into the unique index first
		if (request.employeeCode() != null) {
			requireUniqueEmployeeCode(NexHomeText.upperCase(request.employeeCode()), profile.getId());
		}
		adminProfileMapper.updateAdminProfileEntity(profile, request, actingUserId);
		requireCompleteAddressOrNone(profile);
		if (profile.getPostalCode() != null) {
			requirePostalCodeForCountry(profile.getCountryCode(), profile.getPostalCode());
		}

		log.info("Staff profile {} updated", profile.getPublicId());
		return adminProfileMapper.toAdminProfileDto(profile);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeAdminProfileResponse getAdminProfile(Long adminProfileId) {
		NexHomeAdminProfile profile = findAdminProfile(adminProfileId);
		Long ownerId = profile.getUser().getId();
		if (!ownerId.equals(permissionService.currentUserId())
				&& !permissionService.canManageStaff(ownerId, NexHomeAccess.READ.name())) {
			throw new AccessDeniedException("No read access to this staff profile.");
		}
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
	 * Staff may maintain their own names and address. Employee details, and anyone else's profile, need write on the
	 * owner's staff role function: ADMIN_MANAGE for an admin, SUB_ADMIN_MANAGE for a sub-admin; a super user's
	 * profile can only be changed by a super user (other than by its owner).
	 */
	private void requireStaffManagerFor(Long ownerId, Long actingUserId, boolean employeeDetails) {
		boolean self = ownerId.equals(actingUserId);
		if ((!self || employeeDetails) && !permissionService.canManageStaff(ownerId, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("No write access to this staff profile's employee details.");
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

	/**
	 * A staff profile created from the admin console has no address yet (V8); once one is given, its four required
	 * parts must all be there (also {@code ck_nexhome_admin_profiles_address}).
	 */
	private static void requireCompleteAddressOrNone(NexHomeAdminProfile profile) {
		long given = Stream.of(profile.getAddressLine1(), profile.getCity(), profile.getState(),
				profile.getPostalCode()).filter(Objects::nonNull).count();
		if (given != 0 && given != 4) {
			throw new InvalidRequestException("Address line 1, city, state and postal code must be given together.");
		}
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

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeAuditEntry> getCustomerProfileActivity(Long customerProfileId) {
		NexHomeCustomerProfile profile = findCustomerProfile(customerProfileId);
		NexHomeUser user = profile.getUser();
		requireSelfOrCan(user.getId(), CUSTOMER_MANAGE, NexHomeAccess.READ);
		List<NexHomeAuditUsers.Line> lines = accountLines(user);
		profileLines(lines, profile.getCreationDate(), profile.getCreatedBy(), profile.getUpdatedDate(),
				profile.getUpdatedBy());
		addressRepository.findByUserIdOrderByDefaultAddressDescCreationDateDescIdDesc(user.getId())
				.forEach(address -> {
					String subject = (address.getLabel() != null ? address.getLabel() : address.getAddressType())
							+ ", " + address.getCity();
					lines.add(new NexHomeAuditUsers.Line("ADDRESS_ADDED", subject, address.getCreationDate(),
							address.getCreatedBy()));
					if (NexHomeAuditUsers.updatedLater(address.getCreationDate(), address.getUpdatedDate())) {
						lines.add(new NexHomeAuditUsers.Line("ADDRESS_UPDATED", subject, address.getUpdatedDate(),
								address.getUpdatedBy()));
					}
				});
		return auditUsers.newestFirst(lines);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeAuditEntry> getAdminProfileActivity(Long adminProfileId) {
		NexHomeAdminProfile profile = findAdminProfile(adminProfileId);
		NexHomeUser user = profile.getUser();
		if (!user.getId().equals(permissionService.currentUserId())
				&& !permissionService.canManageStaff(user.getId(), NexHomeAccess.READ.name())) {
			throw new AccessDeniedException("No read access to this staff profile.");
		}
		List<NexHomeAuditUsers.Line> lines = accountLines(user);
		profileLines(lines, profile.getCreationDate(), profile.getCreatedBy(), profile.getUpdatedDate(),
				profile.getUpdatedBy());
		return auditUsers.newestFirst(lines);
	}

	/** The account: created, last updated (email, photo, phone) and its last status change. */
	private static List<NexHomeAuditUsers.Line> accountLines(NexHomeUser user) {
		List<NexHomeAuditUsers.Line> lines = new ArrayList<>();
		lines.add(new NexHomeAuditUsers.Line("ACCOUNT_CREATED", null, user.getCreationDate(), user.getCreatedBy()));
		if (NexHomeAuditUsers.updatedLater(user.getCreationDate(), user.getUpdatedDate())) {
			lines.add(new NexHomeAuditUsers.Line("ACCOUNT_UPDATED", null, user.getUpdatedDate(), user.getUpdatedBy()));
		}
		if (user.getStatusUpdatedDate() != null) {
			lines.add(new NexHomeAuditUsers.Line("STATUS_UPDATED", user.getAccountStatus(),
					user.getStatusUpdatedDate(), user.getStatusUpdatedBy()));
		}
		return lines;
	}

	private static void profileLines(List<NexHomeAuditUsers.Line> lines, Instant creationDate, Long createdBy,
			Instant updatedDate, Long updatedBy) {
		lines.add(new NexHomeAuditUsers.Line("PROFILE_CREATED", null, creationDate, createdBy));
		if (NexHomeAuditUsers.updatedLater(creationDate, updatedDate)) {
			lines.add(new NexHomeAuditUsers.Line("PROFILE_UPDATED", null, updatedDate, updatedBy));
		}
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
