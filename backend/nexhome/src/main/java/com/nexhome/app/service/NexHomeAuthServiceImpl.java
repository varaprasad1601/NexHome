package com.nexhome.app.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.config.NexHomeOtpProperties;
import com.nexhome.app.config.NexHomeSecurityProperties;
import com.nexhome.app.config.NexHomeSessionTokenValidator;
import com.nexhome.app.dto.NexHomeAuthOtpCreateRequest;
import com.nexhome.app.dto.NexHomeAuthOtpResponse;
import com.nexhome.app.dto.NexHomeAuthOtpVerifyRequest;
import com.nexhome.app.dto.NexHomeAuthPhoneChangeRequest;
import com.nexhome.app.dto.NexHomeAuthPhoneChangeVerifyRequest;
import com.nexhome.app.dto.NexHomeAuthRefreshTokenRequest;
import com.nexhome.app.dto.NexHomeAuthRegisterRequest;
import com.nexhome.app.dto.NexHomeAuthTokenResponse;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.exception.TooManyRequestsException;
import com.nexhome.app.exception.UnauthorizedException;
import com.nexhome.app.mapper.NexHomeAuthOtpRequestMapper;
import com.nexhome.app.mapper.NexHomeAuthRefreshTokenMapper;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeAuthOtpRequest;
import com.nexhome.app.model.NexHomeAuthRefreshToken;
import com.nexhome.app.model.NexHomeAuthRefreshToken.RevokedReason;
import com.nexhome.app.model.NexHomeLookupCodes.AccountStatus;
import com.nexhome.app.model.NexHomeLookupCodes.OtpPurpose;
import com.nexhome.app.model.NexHomeLookupCodes.OtpRequestStatus;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.model.NexHomeUserRole;
import com.nexhome.app.repository.NexHomeAuthOtpRequestRepository;
import com.nexhome.app.repository.NexHomeAuthRefreshTokenRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeAuthServiceImpl implements NexHomeAuthService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String INVALID_REFRESH_TOKEN = "The refresh token is invalid or has expired.";

	private final NexHomeAuthOtpRequestRepository otpRequestRepository;
	private final NexHomeAuthRefreshTokenRepository refreshTokenRepository;
	private final NexHomeUserRepository userRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeRoleRepository roleRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeAuthOtpRequestMapper otpRequestMapper;
	private final NexHomeAuthRefreshTokenMapper refreshTokenMapper;
	private final NexHomeUserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final NexHomeOtpProperties otpProperties;
	private final NexHomeSecurityProperties securityProperties;

	@Override
	@Transactional
	public NexHomeAuthOtpResponse register(NexHomeAuthRegisterRequest request) {
		String countryCode = request.countryCode().trim();
		String mobileNumber = request.mobileNumber().trim();
		String email = normalizeEmail(request.email());

		if (userRepository.findByCountryCodeAndMobileNumber(countryCode, mobileNumber).isPresent()) {
			throw new DuplicateResourceException("An account already exists with this mobile number. Log in instead.");
		}
		if (email != null && userRepository.findByEmail(email).isPresent()) {
			throw new DuplicateResourceException("An account already exists with this email");
		}
		NexHomeRole role = roleRepository.findByCode(request.roleCode().trim())
				.filter(NexHomeRole::isActiveFlag)
				.orElseThrow(() -> new ResourceNotFoundException("Role Not Found or Inactive"));

		// Nobody is signed in: the new user is their own creator
		NexHomeUser user = userRepository.save(userMapper.toUserEntity(countryCode, mobileNumber, email, null, null));
		user.setCreatedBy(user.getId());
		user.setUpdatedBy(user.getId());
		userRoleRepository.save(userMapper.toUserRoleEntity(user, role, user.getId()));

		log.info("User registered with User ID {} with Role {}", user.getId(), role.getCode());
		return otpRequestMapper.toAuthOtpDto(issueOtp(countryCode, mobileNumber, OtpPurpose.REGISTRATION, user),
				null, null);
	}

	@Override
	@Transactional
	public NexHomeAuthOtpResponse requestOtp(NexHomeAuthOtpCreateRequest request) {
		String countryCode = request.countryCode().trim();
		String mobileNumber = request.mobileNumber().trim();
		String purpose = validateLoginPurpose(request.purpose());

		NexHomeUser user = userRepository.findByCountryCodeAndMobileNumber(countryCode, mobileNumber)
				.orElseThrow(() -> new ResourceNotFoundException("No account exists with this mobile number."));
		requireActive(user);
		if (OtpPurpose.REGISTRATION.equals(purpose) && user.getAccountVerifiedDate() != null) {
			throw new InvalidRequestException("This mobile number is already verified. Log in instead.");
		}

		return otpRequestMapper.toAuthOtpDto(issueOtp(countryCode, mobileNumber, purpose, user), null, null);
	}

	/**
	 * Failed attempts must be saved, so the transaction still commits when the OTP is wrong, expired or locked
	 * (InvalidRequestException); nothing else is changed before those checks pass.
	 */
	@Override
	@Transactional(noRollbackFor = InvalidRequestException.class)
	public NexHomeAuthOtpResponse verifyOtp(NexHomeAuthOtpVerifyRequest request, String userAgent) {
		String countryCode = request.countryCode().trim();
		String mobileNumber = request.mobileNumber().trim();
		String purpose = validateLoginPurpose(request.purpose());

		NexHomeAuthOtpRequest otpRequest = checkOtp(countryCode, mobileNumber, purpose, request.otp(), null);
		NexHomeUser user = otpRequest.getUser();
		// Checked before the OTP is consumed, so a rejected request leaves it usable
		requireActive(user);

		Instant now = Instant.now();
		markVerified(otpRequest, now);
		// Both purposes prove the user owns the number, and both sign the user in
		if (user.getAccountVerifiedDate() == null) {
			user.setAccountVerifiedDate(now);
		}
		user.setLastLoginAt(now);
		user.setUpdatedBy(user.getId());

		NexHomeAuthTokenResponse tokens = startSession(user, UUID.randomUUID(), userAgent, now).tokens();
		log.info("User ID {} signed in with a {} OTP", user.getId(), purpose);
		return otpRequestMapper.toAuthOtpDto(otpRequest, toUserDto(user), tokens);
	}

	/**
	 * A revoked token presented again means it leaked or the client misbehaved, so every session of the user ends.
	 * Those revocations must be saved although the request fails, hence no rollback for UnauthorizedException.
	 */
	@Override
	@Transactional(noRollbackFor = UnauthorizedException.class)
	public NexHomeAuthTokenResponse refreshTokens(NexHomeAuthRefreshTokenRequest request, String userAgent) {
		NexHomeAuthRefreshToken current = refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
				.orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN));
		NexHomeUser user = current.getUser();
		Instant now = Instant.now();

		if (current.getRevokedAt() != null) {
			if (RevokedReason.ROTATED.equals(current.getRevokedReason())) {
				int revoked = refreshTokenRepository.revokeAllByUserId(user.getId(), RevokedReason.REUSE_DETECTED, now,
						user.getId());
				log.warn("Refresh token {} of User ID {} was reused; {} sessions revoked", current.getId(),
						user.getId(), revoked);
			}
			throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
		}
		if (!now.isBefore(current.getExpiresAt())) {
			throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
		}
		if (!AccountStatus.ACTIVE.equals(user.getAccountStatus())) {
			refreshTokenRepository.revokeAllByUserId(user.getId(), RevokedReason.ACCOUNT_STATUS_CHANGED, now,
					user.getId());
			throw new UnauthorizedException("This account is not active.");
		}

		// Same login session: access tokens issued before this refresh stay valid until they expire
		Session next = startSession(user, current.getSessionId(), userAgent, now);
		current.setLastUsedAt(now);
		current.setRevokedAt(now);
		current.setRevokedReason(RevokedReason.ROTATED);
		current.setReplacedBy(next.refreshToken());
		current.setUpdatedBy(user.getId());

		log.debug("Refresh token {} of User ID {} rotated", current.getId(), user.getId());
		return next.tokens();
	}

	@Override
	@Transactional
	public void logout(NexHomeAuthRefreshTokenRequest request) {
		refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
				.filter(token -> token.getRevokedAt() == null)
				.ifPresent(token -> {
					token.setRevokedAt(Instant.now());
					token.setRevokedReason(RevokedReason.LOGOUT);
					token.setUpdatedBy(token.getUser().getId());
					log.info("User ID {} logged out of session {}", token.getUser().getId(), token.getId());
				});
	}

	@Override
	@Transactional
	public void logoutAll() {
		Long userId = permissionService.currentUserId();
		int revoked = refreshTokenRepository.revokeAllByUserId(userId, RevokedReason.LOGOUT_ALL, Instant.now(),
				userId);
		log.info("User ID {} logged out of all {} sessions", userId, revoked);
	}

	@Override
	@Transactional
	public NexHomeAuthOtpResponse requestPhoneChangeOtp(NexHomeAuthPhoneChangeRequest request) {
		String countryCode = request.countryCode().trim();
		String mobileNumber = request.mobileNumber().trim();
		NexHomeUser user = currentUser();
		requireActive(user);
		// The OTP goes to the new number, which no account may use yet
		if (userRepository.findByCountryCodeAndMobileNumber(countryCode, mobileNumber).isPresent()) {
			throw new DuplicateResourceException("An account already exists with this mobile number");
		}

		return otpRequestMapper.toAuthOtpDto(issueOtp(countryCode, mobileNumber, OtpPurpose.PHONE_CHANGE, user), null,
				null);
	}

	@Override
	@Transactional(noRollbackFor = InvalidRequestException.class)
	public NexHomeAuthOtpResponse verifyPhoneChangeOtp(NexHomeAuthPhoneChangeVerifyRequest request) {
		String countryCode = request.countryCode().trim();
		String mobileNumber = request.mobileNumber().trim();
		NexHomeUser user = currentUser();

		NexHomeAuthOtpRequest otpRequest = checkOtp(countryCode, mobileNumber, OtpPurpose.PHONE_CHANGE,
				request.otp(), user.getId());
		requireActive(user);
		if (userRepository.findByCountryCodeAndMobileNumber(countryCode, mobileNumber).isPresent()) {
			throw new DuplicateResourceException("An account already exists with this mobile number");
		}

		markVerified(otpRequest, Instant.now());
		user.setCountryCode(countryCode);
		user.setMobileNumber(mobileNumber);
		user.setUpdatedBy(user.getId());

		log.info("Mobile number of User ID {} changed", user.getId());
		return otpRequestMapper.toAuthOtpDto(otpRequest, toUserDto(user), null);
	}

	/** Only LOGIN and REGISTRATION are verified through the public OTP endpoints. */
	private String validateLoginPurpose(String requested) {
		String purpose = lookupService.validateCode(Types.OTP_PURPOSE, requested, "Purpose");
		return switch (purpose) {
			case OtpPurpose.LOGIN, OtpPurpose.REGISTRATION -> purpose;
			case OtpPurpose.PHONE_CHANGE ->
				throw new InvalidRequestException("Use the phone change endpoints to change a mobile number.");
			case OtpPurpose.SERVICE_START, OtpPurpose.SERVICE_COMPLETION ->
				throw new InvalidRequestException("Booking OTPs are not available yet.");
			default -> throw new InvalidRequestException("OTPs for " + purpose + " are not supported yet.");
		};
	}

	/**
	 * Creates a PENDING OTP after the resend cooldown. The previous pending OTP for the number and purpose expires
	 * first, flushed before the insert because IDENTITY inserts run immediately and the one-pending index would reject
	 * them.
	 */
	private NexHomeAuthOtpRequest issueOtp(String countryCode, String mobileNumber, String purpose, NexHomeUser user) {
		Instant now = Instant.now();
		otpRequestRepository.findFirstByCountryCodeAndMobileNumberAndPurposeOrderByCreationDateDesc(countryCode,
				mobileNumber, purpose).ifPresent(latest -> {
					if (latest.getCreationDate()
							.isAfter(now.minusSeconds(otpProperties.resendCooldownSeconds()))) {
						throw new TooManyRequestsException("Please wait before requesting another OTP.");
					}
				});
		otpRequestRepository.findByCountryCodeAndMobileNumberAndPurposeAndRequestStatus(countryCode, mobileNumber,
				purpose, OtpRequestStatus.PENDING).ifPresent(previous -> {
					previous.setRequestStatus(OtpRequestStatus.EXPIRED);
					previous.setUpdatedBy(user.getId());
					otpRequestRepository.saveAndFlush(previous);
				});

		String otp = generateOtp();
		NexHomeAuthOtpRequest otpRequest = otpRequestRepository.save(otpRequestMapper.toAuthOtpRequestEntity(
				countryCode, mobileNumber, purpose, user, passwordEncoder.encode(otp),
				now.plus(Duration.ofMinutes(otpProperties.expiryMinutes())), user.getId()));

		// TODO: send the OTP by SMS once a provider is integrated
		if (otpProperties.logPlaintext()) {
			log.info("OTP {} for {} {} ({}): {}", otpRequest.getId(), countryCode, mobileNumber, purpose, otp);
		}
		else {
			log.info("OTP {} created for User ID {} ({})", otpRequest.getId(), user.getId(), purpose);
		}
		return otpRequest;
	}

	/**
	 * Finds the pending OTP and checks it; returns it still PENDING when the OTP matches. Expiry and wrong attempts
	 * are recorded on the entity before the InvalidRequestException.
	 *
	 * @param expectedUserId when not null, the OTP must belong to this user (phone change)
	 */
	private NexHomeAuthOtpRequest checkOtp(String countryCode, String mobileNumber, String purpose, String otp,
			Long expectedUserId) {
		NexHomeAuthOtpRequest otpRequest = otpRequestRepository
				.findByCountryCodeAndMobileNumberAndPurposeAndRequestStatus(countryCode, mobileNumber, purpose,
						OtpRequestStatus.PENDING)
				.filter(pending -> expectedUserId == null || expectedUserId.equals(pending.getUser().getId()))
				.orElseThrow(() -> new InvalidRequestException("No pending OTP for this mobile number. "
						+ "Request a new one."));
		Long ownerId = otpRequest.getUser().getId();

		if (!Instant.now().isBefore(otpRequest.getExpiresAt())) {
			otpRequest.setRequestStatus(OtpRequestStatus.EXPIRED);
			otpRequest.setUpdatedBy(ownerId);
			throw new InvalidRequestException("OTP has expired. Request a new one.");
		}
		if (!passwordEncoder.matches(otp, otpRequest.getOtpHash())) {
			short attempts = (short) (otpRequest.getAttemptCount() + 1);
			otpRequest.setAttemptCount(attempts);
			otpRequest.setUpdatedBy(ownerId);
			if (attempts >= otpProperties.maxAttempts()) {
				otpRequest.setRequestStatus(OtpRequestStatus.LOCKED);
				log.info("OTP {} locked after {} wrong attempts", otpRequest.getId(), attempts);
				throw new InvalidRequestException("Too many wrong attempts. Request a new OTP.");
			}
			throw new InvalidRequestException("OTP is incorrect.");
		}
		return otpRequest;
	}

	private static void markVerified(NexHomeAuthOtpRequest otpRequest, Instant now) {
		otpRequest.setRequestStatus(OtpRequestStatus.VERIFIED);
		otpRequest.setVerifiedAt(now);
		otpRequest.setUpdatedBy(otpRequest.getUser().getId());
	}

	/**
	 * Issues an access token and stores a new refresh token (only its hash) for the login session. The access token
	 * carries the session id ({@code sid}); NexHomeSessionTokenValidator rejects it once the session is revoked.
	 */
	private Session startSession(NexHomeUser user, UUID sessionId, String userAgent, Instant now) {
		List<String> roles = userRoleRepository.findByUserIdOrderByRoleIdAsc(user.getId()).stream()
				.filter(userRole -> userRole.isActive() && userRole.getRole().isActiveFlag())
				.map(NexHomeUserRole::getRole)
				.map(NexHomeRole::getCode)
				.toList();
		// JWT times are whole seconds; truncate so the reported expiry matches the token's exp claim
		Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
		Instant accessTokenExpiresAt = issuedAt.plus(Duration.ofMinutes(securityProperties.accessTokenMinutes()));
		// The roles are informational for clients; permissions are always checked against the database
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(securityProperties.jwtIssuer())
				.subject(user.getId().toString())
				.id(UUID.randomUUID().toString())
				.issuedAt(issuedAt)
				.expiresAt(accessTokenExpiresAt)
				.claim(NexHomeSessionTokenValidator.SESSION_ID_CLAIM, sessionId.toString())
				.claim("roles", roles)
				.build();
		String accessToken = jwtEncoder.encode(
				JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

		byte[] random = new byte[32];
		RANDOM.nextBytes(random);
		String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
		Instant refreshTokenExpiresAt = now.plus(Duration.ofDays(securityProperties.refreshTokenDays()));
		NexHomeAuthRefreshToken stored = refreshTokenRepository.save(refreshTokenMapper.toRefreshTokenEntity(user,
				sessionId, hashToken(refreshToken), refreshTokenExpiresAt, userAgent));

		return new Session(new NexHomeAuthTokenResponse("Bearer", accessToken, accessTokenExpiresAt, refreshToken,
				refreshTokenExpiresAt), stored);
	}

	/**
	 * Refresh tokens are 256-bit random values, so a fast unsalted SHA-256 is enough and lets them be looked up by
	 * hash.
	 */
	private static String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.trim().getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	private NexHomeUser currentUser() {
		return userRepository.findById(permissionService.currentUserId())
				.orElseThrow(() -> new UnauthorizedException("Authentication is required."));
	}

	private NexHomeUserResponse toUserDto(NexHomeUser user) {
		return userMapper.toUserDto(user, userRoleRepository.findByUserIdOrderByRoleIdAsc(user.getId()));
	}

	private static void requireActive(NexHomeUser user) {
		if (!AccountStatus.ACTIVE.equals(user.getAccountStatus())) {
			throw new InvalidRequestException("This account is not active.");
		}
	}

	private static String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String generateOtp() {
		StringBuilder otp = new StringBuilder(otpProperties.length());
		for (int i = 0; i < otpProperties.length(); i++) {
			otp.append(RANDOM.nextInt(10));
		}
		return otp.toString();
	}

	private record Session(NexHomeAuthTokenResponse tokens, NexHomeAuthRefreshToken refreshToken) {
	}
}
