package com.nexhome.app.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeFunctionAccessResponse;
import com.nexhome.app.dto.NexHomeUserPermissionsResponse;
import com.nexhome.app.exception.UnauthorizedException;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeFunction;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeRoleFunction;
import com.nexhome.app.repository.NexHomeFunctionRepository;
import com.nexhome.app.repository.NexHomeRoleFunctionRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service("permissions")
@RequiredArgsConstructor
public class NexHomePermissionServiceImpl implements NexHomePermissionService {

	private final NexHomeUserRepository userRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeRoleFunctionRepository roleFunctionRepository;
	private final NexHomeFunctionRepository functionRepository;

	/** The function of each role's accounts; SUPER_USER and other roles have none (super users only). */
	private static final Map<String, String> ROLE_FUNCTIONS = Map.of(NexHomeRole.ADMIN, "ADMIN_MANAGE",
			NexHomeRole.SUB_ADMIN, "SUB_ADMIN_MANAGE", NexHomeRole.CUSTOMER, "CUSTOMER_MANAGE",
			NexHomeRole.PARTNER, "PARTNER_MANAGE");
	private static final List<String> STAFF_ROLES = List.of(NexHomeRole.SUPER_USER, NexHomeRole.ADMIN,
			NexHomeRole.SUB_ADMIN);

	@Override
	public Long currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication instanceof AnonymousAuthenticationToken
				|| !authentication.isAuthenticated()) {
			throw new UnauthorizedException("Authentication is required.");
		}
		// Access tokens carry the user id as their subject
		String subject = authentication instanceof JwtAuthenticationToken jwt ? jwt.getToken().getSubject()
				: authentication.getName();
		try {
			return Long.valueOf(subject);
		}
		catch (NumberFormatException ex) {
			throw new UnauthorizedException("Authentication is required.");
		}
	}

	@Override
	@Transactional(readOnly = true)
	public boolean can(String functionCode, String access) {
		NexHomeAccess requested = NexHomeAccess.valueOf(access);
		Long userId = currentUserId();
		boolean allowed = hasAccess(userId, functionCode, requested);
		if (!allowed) {
			log.info("User ID {} denied {} access to {}", userId, requested, functionCode);
		}
		return allowed;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean canIfSignedIn(String functionCode, String access) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication instanceof AnonymousAuthenticationToken
				|| !authentication.isAuthenticated()) {
			return false;
		}
		// Not logged when denied: public endpoints ask this of every signed-in visitor
		return hasAccess(currentUserId(), functionCode, NexHomeAccess.valueOf(access));
	}

	/** An ACTIVE super user, or an ACTIVE account with an active role function granting the access. */
	private boolean hasAccess(Long userId, String functionCode, NexHomeAccess requested) {
		if (!isActiveAccount(userId)) {
			return false;
		}
		if (isSuperUser(userId)) {
			return true;
		}
		return roleFunctionRepository.hasAccess(userId, functionCode, requested == NexHomeAccess.READ,
				requested == NexHomeAccess.WRITE, requested == NexHomeAccess.DELETE);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isSelfOrCan(Long userId, String functionCode, String access) {
		Long currentUserId = currentUserId();
		if (currentUserId.equals(userId)) {
			return isActiveAccount(currentUserId);
		}
		return can(functionCode, access);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isSuperUser(Long userId) {
		return userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(userId,
				NexHomeRole.SUPER_USER);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isCurrentUserSuperUser() {
		Long userId = currentUserId();
		boolean allowed = isActiveAccount(userId) && isSuperUser(userId);
		if (!allowed) {
			log.info("User ID {} denied a super-user-only action", userId);
		}
		return allowed;
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeUserPermissionsResponse currentUserPermissions() {
		Long userId = currentUserId();
		if (!isActiveAccount(userId)) {
			return new NexHomeUserPermissionsResponse(false, List.of());
		}
		if (isSuperUser(userId)) {
			return new NexHomeUserPermissionsResponse(true,
					functionRepository.findByActiveFlagTrueOrderByFunctionModuleAscFunctionCodeAsc().stream()
							.map(function -> new NexHomeFunctionAccessResponse(function.getFunctionModule(),
									function.getFunctionCode(), true, true, true))
							.toList());
		}
		// One entry per function: a flag is granted when any of the user's roles grants it
		Map<String, NexHomeFunctionAccessResponse> byCode = new TreeMap<>();
		for (NexHomeRoleFunction grant : roleFunctionRepository.findActiveGrantsOfUser(userId)) {
			NexHomeFunction function = grant.getFunction();
			byCode.merge(function.getFunctionCode(),
					new NexHomeFunctionAccessResponse(function.getFunctionModule(), function.getFunctionCode(),
							grant.isCanRead(), grant.isCanWrite(), grant.isCanDelete()),
					(a, b) -> new NexHomeFunctionAccessResponse(a.functionModule(), a.functionCode(),
							a.canRead() || b.canRead(), a.canWrite() || b.canWrite(), a.canDelete() || b.canDelete()));
		}
		return new NexHomeUserPermissionsResponse(false, byCode.values().stream()
				.sorted(Comparator.comparing(NexHomeFunctionAccessResponse::functionModule)
						.thenComparing(NexHomeFunctionAccessResponse::functionCode))
				.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public boolean canManageRole(String roleCode, String access) {
		String functionCode = roleCode == null ? null : ROLE_FUNCTIONS.get(roleCode);
		if (functionCode == null) {
			Long userId = currentUserId();
			return isActiveAccount(userId) && isSuperUser(userId);
		}
		return can(functionCode, access);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean canManageStaff(Long userId, String access) {
		String role = highestStaffRole(userId);
		return role != null && canManageRole(role, access);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean canManageAccount(Long userId, String access) {
		String staffRole = highestStaffRole(userId);
		if (staffRole != null) {
			return canManageRole(staffRole, access);
		}
		List<String> roles = userRoleRepository.findByUserIdOrderByRoleIdAsc(userId).stream()
				.filter(userRole -> userRole.isActive() && userRole.getRole().isActiveFlag())
				.map(userRole -> userRole.getRole().getCode())
				.toList();
		return roles.isEmpty() ? canManageRole(null, access)
				: roles.stream().anyMatch(code -> canManageRole(code, access));
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isSelfOrCanManageAccount(Long userId, String access) {
		Long currentUserId = currentUserId();
		if (currentUserId.equals(userId)) {
			return isActiveAccount(currentUserId);
		}
		return canManageAccount(userId, access);
	}

	@Override
	@Transactional(readOnly = true)
	public String highestStaffRole(Long userId) {
		return STAFF_ROLES.stream()
				.filter(code -> userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(userId,
						code))
				.findFirst()
				.orElse(null);
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> readableStaffRoles() {
		Long userId = currentUserId();
		// Not through canManageRole: a role the caller cannot read is not a denial to log here
		return STAFF_ROLES.stream()
				.filter(code -> NexHomeRole.SUPER_USER.equals(code)
						? isActiveAccount(userId) && isSuperUser(userId)
						: hasAccess(userId, ROLE_FUNCTIONS.get(code), NexHomeAccess.READ))
				.toList();
	}

	/** A suspended, blocked or deactivated account keeps no permissions, even with an unexpired access token. */
	private boolean isActiveAccount(Long userId) {
		return userRepository.findById(userId)
				.map(user -> AccountStatus.ACTIVE.equals(user.getAccountStatus()))
				.orElse(false);
	}
}
