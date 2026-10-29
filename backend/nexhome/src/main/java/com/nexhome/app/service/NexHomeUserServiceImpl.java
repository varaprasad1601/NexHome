package com.nexhome.app.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeCustomerResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomePartnerResponse;
import com.nexhome.app.dto.NexHomePartnerStatsResponse;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserRoleAssignRequest;
import com.nexhome.app.dto.NexHomeUserRoleUpdateRequest;
import com.nexhome.app.dto.NexHomeStaffResponse;
import com.nexhome.app.dto.NexHomeStaffStatsResponse;
import com.nexhome.app.dto.NexHomeUserStatsResponse;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeAdminProfileMapper;
import com.nexhome.app.mapper.NexHomeCustomerProfileMapper;
import com.nexhome.app.mapper.NexHomePartnerProfileMapper;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeAdminProfile;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomePartnerProfile;
import com.nexhome.app.model.NexHomeAuthRefreshToken.RevokedReason;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeLookupCodes.PartnerOnboardingStatus;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserRole;
import com.nexhome.app.model.NexHomeUserRoleId;
import com.nexhome.app.repository.NexHomeAdminProfileRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerTimeOffRepository;
import com.nexhome.app.repository.NexHomeAuthRefreshTokenRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeUserServiceImpl implements NexHomeUserService {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final int STATS_MONTHS = 10;
	/** Highest first: a staff member's displayed role is the first one they hold. */
	private static final List<String> STAFF_ROLES = List.of(NexHomeRole.SUPER_USER, NexHomeRole.ADMIN,
			NexHomeRole.SUB_ADMIN);

	private final NexHomeUserRepository userRepository;
	private final NexHomeRoleRepository roleRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeAuthRefreshTokenRepository refreshTokenRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeAuditRecorder auditRecorder;
	private final NexHomeUserMapper userMapper;
	private final NexHomeAdminProfileRepository adminProfileRepository;
	private final NexHomeAdminProfileMapper adminProfileMapper;
	private final NexHomeCustomerProfileRepository customerProfileRepository;
	private final NexHomeCustomerProfileMapper customerProfileMapper;
	private final NexHomePartnerProfileRepository partnerProfileRepository;
	private final NexHomePartnerTimeOffRepository timeOffRepository;
	private final NexHomePartnerProfileMapper partnerProfileMapper;

	@Override
	@Transactional
	public NexHomeUserResponse createUser(NexHomeUserCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		String countryCode = request.countryCode().trim();
		String mobileNumber = request.mobileNumber().trim();
		String email = normalizeEmail(request.email());

		NexHomeRole role = roleRepository.findByIdAndActiveFlagTrue(request.roleId())
				.orElseThrow(() -> new ResourceNotFoundException("Role Not Found or Inactive"));
		requireSuperUserToGrant(role, actingUserId);
		requireRoleManager(role.getCode());

		// One account per mobile number; the same account can hold several roles
		if (userRepository.findByCountryCodeAndMobileNumber(countryCode, mobileNumber).isPresent()) {
			throw new DuplicateResourceException("An account already exists with this mobile number");
		}
		if (email != null && userRepository.findByEmail(email).isPresent()) {
			throw new DuplicateResourceException("An account already exists with this email");
		}

		NexHomeUser user = userRepository.save(userMapper.toUserEntity(countryCode, mobileNumber, email,
				trimToNull(request.profilePhotoUrl()), actingUserId));
		// Flushed so that the response carries assigned_at
		NexHomeUserRole userRole = userRoleRepository.saveAndFlush(
				userMapper.toUserRoleEntity(user, role, actingUserId));

		// Staff accounts get their staff profile (and NHADMI public id) at once; the names come later (My Profile)
		if (STAFF_ROLES.contains(role.getCode())) {
			NexHomeAdminProfile profile = adminProfileRepository.save(
					adminProfileMapper.toAdminProfileEntity(user, actingUserId));
			log.info("Staff profile {} created for User ID {}", profile.getPublicId(), user.getId());
		}
		// Customers and partners get their profile (and NHCUST / NHPART id) at once too
		ensureCustomerProfile(user, role, actingUserId);
		ensurePartnerProfile(user, role, actingUserId);

		auditRecorder.record("CREATED", "USER", user.getId(), null, NexHomeAuditRecorder.values("role", role.getCode(),
				"accountStatus", user.getAccountStatus()), actingUserId);
		log.info("User Created Successfully with User ID {} with Role {}", user.getId(), role.getCode());
		return userMapper.toUserDto(user, List.of(userRole));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeUserResponse getUser(Long userId) {
		return toUserDto(findUser(userId));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeUserResponse getCurrentUser() {
		return toUserDto(findUser(permissionService.currentUserId()));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeUserResponse> getUsersByRole(String roleCode, Integer page, Integer size,
			String accountStatus, String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		String status = accountStatus == null || accountStatus.isBlank() ? null
				: lookupService.validateCode(Types.ACCOUNT_STATUS, accountStatus, "Account status");
		// Emails are stored lower-cased
		String text = NexHomePaging.searchText(search);

		Page<NexHomeUser> users = userRepository.findByActiveRole(roleCode, status, text, pageRequest);

		// All roles of the page's users in one query, not one per user
		Map<Long, List<NexHomeUserRole>> rolesByUser = users.isEmpty() ? Map.of()
				: userRoleRepository.findWithRoleByUserIdIn(users.map(NexHomeUser::getId).toList()).stream()
						.collect(Collectors.groupingBy(userRole -> userRole.getUser().getId()));
		List<NexHomeUserResponse> content = users.stream()
				.map(user -> userMapper.toUserDto(user, rolesByUser.getOrDefault(user.getId(), List.of())))
				.toList();
		return NexHomePaging.toPageResponse(pageRequest, users, content);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeCustomerResponse> getCustomers(Integer page, Integer size, String accountStatus,
			String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		String status = accountStatus == null || accountStatus.isBlank() ? null
				: lookupService.validateCode(Types.ACCOUNT_STATUS, accountStatus, "Account status");

		Page<NexHomeUser> users = userRepository.findAll(NexHomeUserRepository.customerSpec(status,
				NexHomePaging.searchText(search)), pageRequest);

		// Roles and profiles of the page's users in one query each
		List<Long> userIds = users.map(NexHomeUser::getId).toList();
		Map<Long, List<NexHomeUserRole>> rolesByUser = userIds.isEmpty() ? Map.of()
				: userRoleRepository.findWithRoleByUserIdIn(userIds).stream()
						.collect(Collectors.groupingBy(userRole -> userRole.getUser().getId()));
		Map<Long, NexHomeCustomerProfile> profileByUser = userIds.isEmpty() ? Map.of()
				: customerProfileRepository.findByUserIdIn(userIds).stream()
						.collect(Collectors.toMap(profile -> profile.getUser().getId(), profile -> profile));
		List<NexHomeCustomerResponse> content = users.stream()
				.map(user -> userMapper.toCustomerDto(user, rolesByUser.getOrDefault(user.getId(), List.of()),
						profileByUser.get(user.getId())))
				.toList();
		return NexHomePaging.toPageResponse(pageRequest, users, content);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomePartnerResponse> getPartners(Integer page, Integer size, String accountStatus,
			String onboardingStatus, Long mainServiceCategoryId, String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		String status = accountStatus == null || accountStatus.isBlank() ? null
				: lookupService.validateCode(Types.ACCOUNT_STATUS, accountStatus, "Account status");
		String onboarding = onboardingStatus == null || onboardingStatus.isBlank() ? null
				: lookupService.validateCode(Types.PARTNER_ONBOARDING_STATUS, onboardingStatus, "Onboarding status");

		Page<NexHomeUser> users = userRepository.findAll(NexHomeUserRepository.partnerSpec(status,
				onboarding == null ? null : List.of(onboarding),
				// NOT_STARTED also covers a partner account that somehow has no profile row
				PartnerOnboardingStatus.NOT_STARTED.equals(onboarding), null, mainServiceCategoryId, NexHomePaging.searchText(search)),
				pageRequest);

		// Roles and profiles (with their category) of the page's users in one query each
		List<Long> userIds = users.map(NexHomeUser::getId).toList();
		Map<Long, List<NexHomeUserRole>> rolesByUser = userIds.isEmpty() ? Map.of()
				: userRoleRepository.findWithRoleByUserIdIn(userIds).stream()
						.collect(Collectors.groupingBy(userRole -> userRole.getUser().getId()));
		Map<Long, NexHomePartnerProfile> profileByUser = userIds.isEmpty() ? Map.of()
				: partnerProfileRepository.findByUserIdIn(userIds).stream()
						.collect(Collectors.toMap(profile -> profile.getUser().getId(), profile -> profile));
		List<NexHomePartnerResponse> content = users.stream()
				.map(user -> userMapper.toPartnerDto(user, rolesByUser.getOrDefault(user.getId(), List.of()),
						profileByUser.get(user.getId())))
				.toList();
		return NexHomePaging.toPageResponse(pageRequest, users, content);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePartnerStatsResponse getPartnerStats() {
		LocalDate today = LocalDate.now(IST);
		return new NexHomePartnerStatsResponse(
				userRepository.count(NexHomeUserRepository.partnerSpec(null, null, false, null, null)),
				userRepository.count(NexHomeUserRepository.partnerSpec(null,
						List.of(PartnerOnboardingStatus.APPROVED), false, null, null)),
				userRepository.count(NexHomeUserRepository.partnerSpec(null, List.of(
						PartnerOnboardingStatus.UNDER_REVIEW), false, null,
						null)),
				userRepository.count(NexHomeUserRepository.partnerSpec(null, List.of(PartnerOnboardingStatus.NOT_STARTED,
						PartnerOnboardingStatus.DRAFT, PartnerOnboardingStatus.CHANGES_REQUESTED), true, null, null)),
				timeOffRepository.countPartnersOffBetween(today.atStartOfDay(IST).toInstant(),
						today.plusDays(1).atStartOfDay(IST).toInstant()));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeStaffStatsResponse getCustomerStats() {
		return new NexHomeStaffStatsResponse(
				userRepository.count(NexHomeUserRepository.customerSpec(null, null)),
				userRepository.count(NexHomeUserRepository.customerSpec(AccountStatus.ACTIVE, null)),
				userRepository.count(NexHomeUserRepository.customerSpec(AccountStatus.DEACTIVATED, null)),
				userRepository.count(NexHomeUserRepository.customerSpec(AccountStatus.SUSPENDED, null)),
				userRepository.count(NexHomeUserRepository.customerSpec(AccountStatus.BLOCKED, null)));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeStaffResponse> getStaff(Integer page, Integer size, String roleCode,
			String accountStatus, String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		String role = validateStaffRole(roleCode);
		List<String> readable = readableStaffRoles(role);
		String status = accountStatus == null || accountStatus.isBlank() ? null
				: lookupService.validateCode(Types.ACCOUNT_STATUS, accountStatus, "Account status");

		Page<NexHomeUser> users = userRepository.findAll(NexHomeUserRepository.staffSpec(readable,
				hiddenStaffRole(), role, status, NexHomePaging.searchText(search)), pageRequest);

		// Roles and profiles of the page's users in one query each
		List<Long> userIds = users.map(NexHomeUser::getId).toList();
		Map<Long, List<NexHomeUserRole>> rolesByUser = userIds.isEmpty() ? Map.of()
				: userRoleRepository.findWithRoleByUserIdIn(userIds).stream()
						.collect(Collectors.groupingBy(userRole -> userRole.getUser().getId()));
		Map<Long, NexHomeAdminProfile> profileByUser = userIds.isEmpty() ? Map.of()
				: adminProfileRepository.findByUserIdIn(userIds).stream()
						.collect(Collectors.toMap(profile -> profile.getUser().getId(), profile -> profile));
		List<NexHomeStaffResponse> content = users.stream()
				.map(user -> userMapper.toStaffDto(user,
						highestStaffRole(rolesByUser.getOrDefault(user.getId(), List.of())),
						profileByUser.get(user.getId())))
				.toList();
		return NexHomePaging.toPageResponse(pageRequest, users, content);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeStaffStatsResponse getStaffStats(String roleCode) {
		String hidden = hiddenStaffRole();
		String role = validateStaffRole(roleCode);
		List<String> readable = readableStaffRoles(role);
		return new NexHomeStaffStatsResponse(
				countStaff(readable, hidden, role, null),
				countStaff(readable, hidden, role, AccountStatus.ACTIVE),
				countStaff(readable, hidden, role, AccountStatus.DEACTIVATED),
				countStaff(readable, hidden, role, AccountStatus.SUSPENDED),
				countStaff(readable, hidden, role, AccountStatus.BLOCKED));
	}

	/**
	 * The staff roles the caller may read (ADMIN_MANAGE, SUB_ADMIN_MANAGE read; SUPER_USER for super users); 403 when
	 * none, or when the requested role is not among them.
	 */
	private List<String> readableStaffRoles(String requestedRole) {
		List<String> readable = permissionService.readableStaffRoles();
		if (readable.isEmpty() || (requestedRole != null && !readable.contains(requestedRole))) {
			throw new AccessDeniedException("No read access to these staff accounts.");
		}
		return readable;
	}

	/** Null when not given; otherwise one of the staff role codes. */
	private static String validateStaffRole(String roleCode) {
		if (roleCode == null || roleCode.isBlank()) {
			return null;
		}
		String code = roleCode.trim().toUpperCase(Locale.ROOT);
		if (!STAFF_ROLES.contains(code)) {
			throw new InvalidRequestException("Role must be one of: " + String.join(", ", STAFF_ROLES) + ".");
		}
		return code;
	}

	/** Super users are listed and counted only for super users (as GET /super-users). */
	private String hiddenStaffRole() {
		return permissionService.isCurrentUserSuperUser() ? null : NexHomeRole.SUPER_USER;
	}

	private long countStaff(List<String> readable, String hiddenRole, String roleCode, String accountStatus) {
		return userRepository.count(NexHomeUserRepository.staffSpec(readable, hiddenRole, roleCode, accountStatus,
				null));
	}

	/** The first of SUPER_USER, ADMIN, SUB_ADMIN that the account currently holds, or null. */
	private static NexHomeRole highestStaffRole(List<NexHomeUserRole> userRoles) {
		return STAFF_ROLES.stream()
				.flatMap(code -> userRoles.stream()
						.filter(userRole -> userRole.isActive() && userRole.getRole().isActiveFlag()
								&& code.equals(userRole.getRole().getCode())))
				.map(NexHomeUserRole::getRole)
				.findFirst()
				.orElse(null);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeUserStatsResponse getUserStats() {
		YearMonth current = YearMonth.now(IST);
		boolean customers = permissionService.canManageRole(NexHomeRole.CUSTOMER, NexHomeAccess.READ.name());
		boolean partners = permissionService.canManageRole(NexHomeRole.PARTNER, NexHomeAccess.READ.name());
		if (!customers && !partners) {
			throw new AccessDeniedException("No read access to customers or partners.");
		}
		return new NexHomeUserStatsResponse(customers ? roleStats(NexHomeRole.CUSTOMER, current) : null,
				partners ? roleStats(NexHomeRole.PARTNER, current) : null);
	}

	/** Running totals per month from the per-month additions; months before the window count towards its start. */
	private NexHomeUserStatsResponse.RoleStats roleStats(String roleCode, YearMonth current) {
		Map<YearMonth, Long> added = userRoleRepository.countActiveByRoleAndMonth(roleCode).stream()
				.collect(Collectors.toMap(row -> YearMonth.parse(row.getMonth()),
						NexHomeUserRoleRepository.MonthlyCount::getTotal));
		YearMonth first = current.minusMonths(STATS_MONTHS - 1);

		long running = added.entrySet().stream()
				.filter(entry -> entry.getKey().isBefore(first))
				.mapToLong(Map.Entry::getValue)
				.sum();
		List<NexHomeUserStatsResponse.MonthStats> months = new ArrayList<>();
		for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
			long inMonth = added.getOrDefault(month, 0L);
			running += inMonth;
			months.add(new NexHomeUserStatsResponse.MonthStats(month.toString(), inMonth, running));
		}
		// Assignments dated after this month (clock differences) still count towards the total
		long later = added.entrySet().stream()
				.filter(entry -> entry.getKey().isAfter(current))
				.mapToLong(Map.Entry::getValue)
				.sum();
		return new NexHomeUserStatsResponse.RoleStats(roleCode, running + later, added.getOrDefault(current, 0L),
				months);
	}

	@Override
	@Transactional
	public NexHomeUserResponse updateUser(NexHomeUserUpdateRequest request) {
		NexHomeUser user = findUser(request.userId());

		// The mobile number, status and roles are not updated here (the request rejects them)
		if (request.email() != null) {
			String email = normalizeEmail(request.email());
			if (email != null && !email.equals(user.getEmail()) && userRepository.findByEmail(email).isPresent()) {
				throw new DuplicateResourceException("An account already exists with this email");
			}
			user.setEmail(email);
		}
		if (request.profilePhotoUrl() != null) {
			user.setProfilePhotoUrl(trimToNull(request.profilePhotoUrl()));
		}
		user.setUpdatedBy(permissionService.currentUserId());

		log.info("User updated with User ID {}", user.getId());
		return toUserDto(user);
	}

	@Override
	@Transactional
	public NexHomeUserResponse updateUserStatus(NexHomeUserStatusUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeUser user = findUser(request.userId());
		if (user.getId().equals(actingUserId)) {
			throw new InvalidRequestException("You cannot change your own account status.");
		}
		if (permissionService.isSuperUser(user.getId()) && !permissionService.isSuperUser(actingUserId)) {
			throw new AccessDeniedException("Only a super user can change a super user's account status.");
		}
		if (!permissionService.canManageAccount(user.getId(), NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("No write access to this account.");
		}
		String target = lookupService.validateCode(Types.ACCOUNT_STATUS, request.accountStatus(), "Account status");

		String current = user.getAccountStatus();
		if (target.equals(current)) {
			throw new InvalidRequestException("User is already " + current + ".");
		}

		Instant now = Instant.now();
		user.setAccountStatus(target);
		user.setStatusUpdatedBy(actingUserId);
		user.setStatusUpdatedDate(now);
		user.setUpdatedBy(actingUserId);
		if (!AccountStatus.ACTIVE.equals(target)) {
			// The user's access tokens stop working at once too: permission checks require an ACTIVE account
			int sessions = refreshTokenRepository.revokeAllByUserId(user.getId(),
					RevokedReason.ACCOUNT_STATUS_CHANGED, now, actingUserId);
			log.info("{} sessions of User ID {} revoked", sessions, user.getId());
		}

		auditRecorder.record("STATUS_CHANGED", "USER", user.getId(), NexHomeAuditRecorder.values("accountStatus", current),
				NexHomeAuditRecorder.values("accountStatus", target), actingUserId);
		log.info("Account status changed from {} to {} for User ID {}", current, target, request.userId());
		return toUserDto(findUser(request.userId()));
	}

	@Override
	@Transactional
	public NexHomeUserResponse assignRole(NexHomeUserRoleAssignRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeUser user = findUser(request.userId());
		NexHomeRole role = roleRepository.findByIdAndActiveFlagTrue(request.roleId())
				.orElseThrow(() -> new ResourceNotFoundException("Role Not Found or Inactive"));
		requireSuperUserToGrant(role, actingUserId);
		requireRoleManager(role.getCode());

		if (userRoleRepository.existsById(new NexHomeUserRoleId(user.getId(), role.getId()))) {
			throw new DuplicateResourceException("User already has this role");
		}
		userRoleRepository.saveAndFlush(userMapper.toUserRoleEntity(user, role, actingUserId));
		ensureCustomerProfile(user, role, actingUserId);
		ensurePartnerProfile(user, role, actingUserId);

		auditRecorder.record("ROLE_ASSIGNED", "USER", user.getId(), null, NexHomeAuditRecorder.values("role", role.getCode()),
				actingUserId);
		log.info("Role {} assigned to User ID {}", role.getCode(), user.getId());
		return toUserDto(user);
	}

	@Override
	@Transactional
	public NexHomeUserResponse updateUserRole(NexHomeUserRoleUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeUserRole userRole = userRoleRepository.findById(new NexHomeUserRoleId(request.userId(),
				request.roleId()))
				.orElseThrow(() -> new ResourceNotFoundException("User does not have this role."));
		requireSuperUserToGrant(userRole.getRole(), actingUserId);
		requireRoleManager(userRole.getRole().getCode());

		if (request.isActive() && !userRole.getRole().isActiveFlag()) {
			throw new InvalidRequestException("An inactive role cannot be activated for a user.");
		}
		auditRecorder.record("ROLE_UPDATED", "USER", request.userId(), NexHomeAuditRecorder.values("role", userRole.getRole().getCode(),
				"isActive", userRole.isActive()), NexHomeAuditRecorder.values("role", userRole.getRole().getCode(), "isActive",
				request.isActive()), actingUserId);
		userRole.setActive(request.isActive());
		userRole.setUpdatedBy(actingUserId);

		log.info("Role {} of User ID {} set to active: {}", userRole.getRole().getCode(), request.userId(),
				request.isActive());
		return toUserDto(userRole.getUser());
	}

	/**
	 * Creating an account with a role, or giving or withdrawing the role: write on the role's function (ADMIN_MANAGE,
	 * SUB_ADMIN_MANAGE, CUSTOMER_MANAGE, PARTNER_MANAGE; SUPER_USER and other roles: super users).
	 */
	private void requireRoleManager(String roleCode) {
		if (!permissionService.canManageRole(roleCode, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("No write access to accounts of this role.");
		}
	}

	/** A CUSTOMER account without a customer profile gets its empty one (V15), with the NHCUST customer id. */
	private void ensureCustomerProfile(NexHomeUser user, NexHomeRole role, Long actingUserId) {
		if (NexHomeRole.CUSTOMER.equals(role.getCode()) && !customerProfileRepository.existsByUserId(user.getId())) {
			NexHomeCustomerProfile profile = customerProfileRepository.save(
					customerProfileMapper.toCustomerProfileEntity(user, actingUserId));
			log.info("Customer profile {} created for User ID {}", profile.getPublicId(), user.getId());
		}
	}

	/** A PARTNER account without a partner profile gets its empty NOT_STARTED one (V16, V18), with its NHPART id. */
	private void ensurePartnerProfile(NexHomeUser user, NexHomeRole role, Long actingUserId) {
		if (NexHomeRole.PARTNER.equals(role.getCode()) && !partnerProfileRepository.existsByUserId(user.getId())) {
			NexHomePartnerProfile profile = partnerProfileRepository.save(
					partnerProfileMapper.toPartnerProfileEntity(user, actingUserId));
			log.info("Partner profile {} created for User ID {}", profile.getPublicId(), user.getId());
		}
	}

	/** Anyone allowed to manage users could otherwise make themselves or others super users. */
	private void requireSuperUserToGrant(NexHomeRole role, Long actingUserId) {
		if (NexHomeRole.SUPER_USER.equals(role.getCode()) && !permissionService.isSuperUser(actingUserId)) {
			throw new AccessDeniedException("Only a super user can grant or change the SUPER_USER role.");
		}
	}

	private NexHomeUser findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));
	}

	private NexHomeUserResponse toUserDto(NexHomeUser user) {
		return userMapper.toUserDto(user, userRoleRepository.findByUserIdOrderByRoleIdAsc(user.getId()));
	}

	private static String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
