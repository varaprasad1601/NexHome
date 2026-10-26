package com.nexhome.app.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserRoleAssignRequest;
import com.nexhome.app.dto.NexHomeUserRoleUpdateRequest;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeAuthRefreshToken.RevokedReason;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserRole;
import com.nexhome.app.model.NexHomeUserRoleId;
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

	private final NexHomeUserRepository userRepository;
	private final NexHomeRoleRepository roleRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeAuthRefreshTokenRepository refreshTokenRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeUserMapper userMapper;

	@Override
	@Transactional
	public NexHomeUserResponse createUser(NexHomeUserCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		String countryCode = request.countryCode().trim();
		String mobileNumber = request.mobileNumber().trim();
		String email = normalizeEmail(request.email());

		// One account per mobile number; the same account can hold several roles
		if (userRepository.findByCountryCodeAndMobileNumber(countryCode, mobileNumber).isPresent()) {
			throw new DuplicateResourceException("An account already exists with this mobile number");
		}
		if (email != null && userRepository.findByEmail(email).isPresent()) {
			throw new DuplicateResourceException("An account already exists with this email");
		}
		NexHomeRole role = roleRepository.findByIdAndActiveFlagTrue(request.roleId())
				.orElseThrow(() -> new ResourceNotFoundException("Role Not Found or Inactive"));
		requireSuperUserToGrant(role, actingUserId);

		NexHomeUser user = userRepository.save(userMapper.toUserEntity(countryCode, mobileNumber, email,
				trimToNull(request.profilePhotoUrl()), actingUserId));
		// Flushed so that the response carries assigned_at
		NexHomeUserRole userRole = userRoleRepository.saveAndFlush(
				userMapper.toUserRoleEntity(user, role, actingUserId));

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

		if (userRoleRepository.existsById(new NexHomeUserRoleId(user.getId(), role.getId()))) {
			throw new DuplicateResourceException("User already has this role");
		}
		userRoleRepository.saveAndFlush(userMapper.toUserRoleEntity(user, role, actingUserId));

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

		if (request.isActive() && !userRole.getRole().isActiveFlag()) {
			throw new InvalidRequestException("An inactive role cannot be activated for a user.");
		}
		userRole.setActive(request.isActive());
		userRole.setUpdatedBy(actingUserId);

		log.info("Role {} of User ID {} set to active: {}", userRole.getRole().getCode(), request.userId(),
				request.isActive());
		return toUserDto(userRole.getUser());
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
