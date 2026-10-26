package com.nexhome.app.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.exception.UnauthorizedException;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeRole;
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

	/** A suspended, blocked or deactivated account keeps no permissions, even with an unexpired access token. */
	private boolean isActiveAccount(Long userId) {
		return userRepository.findById(userId)
				.map(user -> AccountStatus.ACTIVE.equals(user.getAccountStatus()))
				.orElse(false);
	}
}
