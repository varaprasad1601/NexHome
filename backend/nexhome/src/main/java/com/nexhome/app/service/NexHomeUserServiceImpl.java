package com.nexhome.app.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeUserAddressRequest;
import com.nexhome.app.dto.NexHomeUserAddressResponse;
import com.nexhome.app.dto.NexHomeUserAddressUpdateRequest;
import com.nexhome.app.dto.NexHomeUserDetailsRequest;
import com.nexhome.app.dto.NexHomeUserDetailsResponse;
import com.nexhome.app.dto.NexHomeUserDetailsUpdateRequest;
import com.nexhome.app.dto.NexHomeUserPasswordSetRequest;
import com.nexhome.app.dto.NexHomeUserPasswordUpdateRequest;
import com.nexhome.app.dto.NexHomeUserCreateRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.dto.NexHomeUserStatusUpdateRequest;
import com.nexhome.app.dto.NexHomeUserUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeUserAddressMapper;
import com.nexhome.app.mapper.NexHomeUserDetailsMapper;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserAddress;
import com.nexhome.app.model.NexHomeUserDetails;
import com.nexhome.app.model.NexHomeUserStatus;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserAddressRepository;
import com.nexhome.app.repository.NexHomeUserDetailsRepository;
import com.nexhome.app.repository.NexHomeUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeUserServiceImpl implements NexHomeUserService {

	private static final Long DEFAULT_LOGGED_IN_USER_ID = 1L;

	private final NexHomeUserRepository userRepository;
	private final NexHomeRoleRepository roleRepository;
	private final NexHomeUserDetailsRepository userDetailsRepository;
	private final NexHomeUserAddressRepository userAddressRepository;
	private final NexHomeUserMapper userMapper;
	private final NexHomeUserDetailsMapper userDetailsMapper;
	private final NexHomeUserAddressMapper userAddressMapper;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public NexHomeUserResponse createUser(NexHomeUserCreateRequest request) {
		String phone = request.phone().trim();
		String email = normalizeEmail(request.email());

		if (userRepository.findByPhoneAndRoleRoleId(phone, request.roleId()).isPresent()) {
			throw new DuplicateResourceException("User already exists with this phone number");
		}

		NexHomeRole role = roleRepository.findByRoleIdAndActiveFlag(request.roleId(), true)
				.orElseThrow(() -> new ResourceNotFoundException("Role Not Found or Inactive"));

        NexHomeUser user = userRepository.save(
				userMapper.toUserEntity(phone, email, role, DEFAULT_LOGGED_IN_USER_ID));

		log.info("User Created Successfully with User ID {} with Role {}", user.getUserId(), role.getRoleName());
		return userMapper.toUserDto(user);
	}

	@Override
	@Transactional
	public NexHomeUserDetailsResponse addUserDetails(NexHomeUserDetailsRequest request) {
		NexHomeUser user = userRepository.findByUserId(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));

		// A user has at most one details record (user_id is unique in nexhome_user_details)
		if (userDetailsRepository.findByUserUserId(user.getUserId()).isPresent()) {
			throw new DuplicateResourceException("User details already exist for this user");
		}

		NexHomeUserDetails details = userDetailsRepository.save(
				userDetailsMapper.toUserDetailsEntity(request, user, DEFAULT_LOGGED_IN_USER_ID));

		log.info("User details added with User Details ID {} for User ID {}", details.getUserDetailsId(),
				user.getUserId());
		return userDetailsMapper.toUserDetailsDto(details);
	}

	@Override
	@Transactional
	public NexHomeUserAddressResponse addUserAddress(NexHomeUserAddressRequest request) {
		NexHomeUser user = userRepository.findByUserId(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));

		// Only an explicit true activates the new address; false or a missing flag saves it as inactive
		boolean active = Boolean.TRUE.equals(request.activeFlag());
		if (active) {
			// Only one active address per user: deactivate the current one first. It must be flushed before the
			// insert below, because IDENTITY inserts run immediately and the one-active index would reject them.
			userAddressRepository.findByUserUserIdAndActiveFlag(user.getUserId(), true).ifPresent(current -> {
				current.setActiveFlag(false);
				current.setLastUpdateBy(DEFAULT_LOGGED_IN_USER_ID);
				userAddressRepository.saveAndFlush(current);
			});
		}

		NexHomeUserAddress address = userAddressRepository.save(
				userAddressMapper.toUserAddressEntity(request, user, active, DEFAULT_LOGGED_IN_USER_ID));

		log.info("User address added with User Address ID {} for User ID {})",
				address.getUserAddressId(), user.getUserId());
		return userAddressMapper.toUserAddressDto(address);
	}

	@Override
	@Transactional
	public NexHomeUserResponse updateUser(NexHomeUserUpdateRequest request) {
		NexHomeUser user = userRepository.findByUserId(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));

		// Phone and role are never updated (the request rejects them, and the columns are not updatable)
		if (request.email() != null) {
			user.setEmail(normalizeEmail(request.email()));
		}
		if (request.activeFlag() != null) {
			// Only a user who has set a password (ACTIVATED) may be active; deactivating is always allowed
			if (request.activeFlag() && user.getUserStatus() != NexHomeUserStatus.ACTIVATED) {
				throw new InvalidRequestException("Only an activated user can be made active.");
			}
			user.setActiveFlag(request.activeFlag());
		}
		user.setLastUpdateBy(DEFAULT_LOGGED_IN_USER_ID);

		log.info("User updated with User ID {}", user.getUserId());
		return userMapper.toUserDto(user);
	}

	@Override
	@Transactional
	public NexHomeUserResponse updatePassword(NexHomeUserPasswordUpdateRequest request) {
		NexHomeUser user = userRepository.findByUserId(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));

		if (user.getUserStatus() != NexHomeUserStatus.ACTIVATED) {
			throw new InvalidRequestException("Password has not been set yet.");
		}
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			log.info("Password update rejected for User ID {}: wrong current password", user.getUserId());
			throw new InvalidRequestException("Current password is incorrect.");
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
			throw new InvalidRequestException("New password must be different from the current password.");
		}

		user.setPassword(passwordEncoder.encode(request.newPassword()));
		user.setLastUpdateBy(DEFAULT_LOGGED_IN_USER_ID);

		log.info("Password updated for User ID {}", user.getUserId());
		return userMapper.toUserDto(user);
	}

	@Override
	@Transactional
	public NexHomeUserResponse updateUserStatus(NexHomeUserStatusUpdateRequest request) {
		NexHomeUser user = userRepository.findByUserId(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));

		NexHomeUserStatus current = user.getUserStatus();
		NexHomeUserStatus target = request.userStatus();
		if (target != NexHomeUserStatus.APPROVED && target != NexHomeUserStatus.REJECTED) {
			throw new InvalidRequestException("User status can only be set to APPROVED or REJECTED.");
		}
		if (current == NexHomeUserStatus.ACTIVATED) {
			throw new InvalidRequestException("An activated user's status cannot be changed.");
		}
		if (current == target) {
			throw new InvalidRequestException("User is already " + current + ".");
		}

		user.setUserStatus(target);
		user.setLastUpdateBy(DEFAULT_LOGGED_IN_USER_ID);
		// TODO: when APPROVED, send the user an email with a link to set their password

		log.info("User status changed from {} to {} for User ID {}", current, target, user.getUserId());
		return userMapper.toUserDto(user);
	}

	@Override
	@Transactional
	public NexHomeUserResponse setPassword(NexHomeUserPasswordSetRequest request) {
		NexHomeUser user = userRepository.findByUserId(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));

		if (user.getUserStatus() == NexHomeUserStatus.ACTIVATED) {
			throw new InvalidRequestException("Password is already set. Use the password update instead.");
		}
		if (user.getUserStatus() != NexHomeUserStatus.APPROVED) {
			throw new InvalidRequestException("User is not approved yet.");
		}
		if (!request.password().equals(request.confirmPassword())) {
			throw new InvalidRequestException("Password and confirm password do not match.");
		}

		user.setPassword(passwordEncoder.encode(request.password()));
		user.setUserStatus(NexHomeUserStatus.ACTIVATED);
		user.setActiveFlag(true);
		// The user sets their own password, so they are the last updater
		user.setLastUpdateBy(user.getUserId());

		log.info("Password set and user activated for User ID {}", user.getUserId());
		return userMapper.toUserDto(user);
	}

	@Override
	@Transactional
	public void deleteUser(Long userId) {
		if (userRepository.findByUserId(userId).isEmpty()) {
			throw new ResourceNotFoundException("User does not exist.");
		}

		// Children first, so no foreign key is violated
		int addresses = userAddressRepository.deleteAllByUserId(userId);
		int details = userDetailsRepository.deleteAllByUserId(userId);
		userRepository.deleteById(userId);

		log.info("User deleted with User ID {} ({} details, {} addresses)", userId, details, addresses);
	}

	@Override
	@Transactional
	public NexHomeUserDetailsResponse updateUserDetails(NexHomeUserDetailsUpdateRequest request) {
		NexHomeUserDetails details = userDetailsRepository.findById(request.userDetailsId())
				.orElseThrow(() -> new ResourceNotFoundException("User details do not exist."));

		// The owning user is never updated (the request rejects userId, and the column is not updatable)
		userDetailsMapper.updateUserDetailsEntity(details, request, DEFAULT_LOGGED_IN_USER_ID);

		log.info("User details updated with User Details ID {}", details.getUserDetailsId());
		return userDetailsMapper.toUserDetailsDto(details);
	}

	@Override
	@Transactional
	public NexHomeUserAddressResponse updateUserAddress(NexHomeUserAddressUpdateRequest request) {
		NexHomeUserAddress address = userAddressRepository.findById(request.userAddressId())
				.orElseThrow(() -> new ResourceNotFoundException("User address does not exist."));

		userAddressMapper.updateUserAddressEntity(address, request, DEFAULT_LOGGED_IN_USER_ID);

		if (Boolean.TRUE.equals(request.activeFlag()) && !address.isActiveFlag()) {
			// Same rule as adding: deactivate the user's current active address and flush before activating this one
			userAddressRepository.findByUserUserIdAndActiveFlag(address.getUser().getUserId(), true)
					.ifPresent(current -> {
						current.setActiveFlag(false);
						current.setLastUpdateBy(DEFAULT_LOGGED_IN_USER_ID);
						userAddressRepository.saveAndFlush(current);
					});
			address.setActiveFlag(true);
		}
		else if (Boolean.FALSE.equals(request.activeFlag())) {
			address.setActiveFlag(false);
		}

		log.info("User address updated with User Address ID {} (active: {})", address.getUserAddressId(),
				address.isActiveFlag());
		return userAddressMapper.toUserAddressDto(address);
	}

	@Override
	@Transactional
	public void deleteUserAddress(Long userAddressId) {
		if (!userAddressRepository.existsById(userAddressId)) {
			throw new ResourceNotFoundException("User address does not exist.");
		}
		// No other address is activated automatically; only an explicit activeFlag=true makes one active
		userAddressRepository.deleteById(userAddressId);

		log.info("User address deleted with User Address ID {}", userAddressId);
	}

	private static String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
