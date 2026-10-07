package com.nexhome.app.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeUserRegisterRequest;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeRoleRepository;
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
	private final NexHomeUserMapper userMapper;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public NexHomeUserResponse register(NexHomeUserRegisterRequest request) {
		String phone = request.phone().trim();
		String email = normalizeEmail(request.email());

		if (userRepository.findByPhoneAndRoleRoleId(phone, request.roleId()).isPresent()) {
			throw new DuplicateResourceException("User already exists with this phone number");
		}

		NexHomeRole role = roleRepository.findByRoleId(request.roleId())
				.filter(NexHomeRole::isActiveFlag)
				.orElseThrow(() -> new InvalidRequestException("Role does not exist or is inactive."));

        NexHomeUser user = userRepository.save(
				userMapper.toUserEntity(phone, email, passwordEncoder.encode(request.password()), role, DEFAULT_LOGGED_IN_USER_ID));

		log.info("User Registered Successfully wiht User ID {} with Role {}", user.getUserId(), role.getRoleName());
		return userMapper.toUserDto(user);
	}

	private static String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
