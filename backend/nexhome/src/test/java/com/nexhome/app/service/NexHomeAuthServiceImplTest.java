package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.config.NexHomeOtpProperties;
import com.nexhome.app.config.NexHomeSecurityProperties;
import com.nexhome.app.dto.NexHomeAuthAdminOtpRequest;
import com.nexhome.app.dto.NexHomeAuthOtpCreateRequest;
import com.nexhome.app.dto.NexHomeAuthOtpResponse;
import com.nexhome.app.dto.NexHomeAuthOtpVerifyRequest;
import com.nexhome.app.dto.NexHomeAuthPhoneChangeRequest;
import com.nexhome.app.dto.NexHomeAuthPhoneChangeVerifyRequest;
import com.nexhome.app.dto.NexHomeAuthRefreshTokenRequest;
import com.nexhome.app.dto.NexHomeAuthRegisterRequest;
import com.nexhome.app.dto.NexHomeAuthTokenResponse;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.exception.TooManyRequestsException;
import com.nexhome.app.exception.UnauthorizedException;
import com.nexhome.app.mapper.NexHomeAuthOtpRequestMapper;
import com.nexhome.app.mapper.NexHomeAuthRefreshTokenMapper;
import com.nexhome.app.mapper.NexHomeCustomerProfileMapper;
import com.nexhome.app.mapper.NexHomePartnerProfileMapper;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeAuthOtpRequest;
import com.nexhome.app.model.NexHomeAuthRefreshToken;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAuthOtpRequestRepository;
import com.nexhome.app.repository.NexHomeAuthRefreshTokenRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

class NexHomeAuthServiceImplTest {

	private static final String CC = "+91";
	private static final String MOBILE = "9876543210";
	private static final SecretKeySpec KEY = new SecretKeySpec(new byte[32], "HmacSHA256");
	private static final UUID SESSION_ID = UUID.fromString("7f8c4a2e-1b3d-4e5f-9a6b-0c1d2e3f4a5b");

	private NexHomeAuthOtpRequestRepository otpRequestRepository;
	private NexHomeAuthRefreshTokenRepository refreshTokenRepository;
	private NexHomeUserRepository userRepository;
	private NexHomeUserRoleRepository userRoleRepository;
	private NexHomeRoleRepository roleRepository;
	private NexHomeCustomerProfileRepository customerProfileRepository;
	private NexHomeLookupService lookupService;
	private NexHomePermissionService permissionService;
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private NexHomeAuthServiceImpl authService;

	@BeforeEach
	void setUp() {
		otpRequestRepository = mock(NexHomeAuthOtpRequestRepository.class);
		refreshTokenRepository = mock(NexHomeAuthRefreshTokenRepository.class);
		userRepository = mock(NexHomeUserRepository.class);
		userRoleRepository = mock(NexHomeUserRoleRepository.class);
		roleRepository = mock(NexHomeRoleRepository.class);
		customerProfileRepository = mock(NexHomeCustomerProfileRepository.class);
		lookupService = mock(NexHomeLookupService.class);
		permissionService = mock(NexHomePermissionService.class);
		when(userRepository.findByCountryCodeAndMobileNumber(any(), any())).thenReturn(Optional.empty());
		when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
		when(otpRequestRepository.findFirstByCountryCodeAndMobileNumberAndPurposeOrderByCreationDateDesc(any(),
				any(), any())).thenReturn(Optional.empty());
		when(otpRequestRepository.findByCountryCodeAndMobileNumberAndPurposeAndRequestStatus(any(), any(), any(),
				any())).thenReturn(Optional.empty());
		when(otpRequestRepository.save(any())).thenAnswer(invocation -> {
			NexHomeAuthOtpRequest otpRequest = invocation.getArgument(0);
			otpRequest.setId(70L);
			return otpRequest;
		});
		when(refreshTokenRepository.save(any())).thenAnswer(invocation -> {
			NexHomeAuthRefreshToken token = invocation.getArgument(0);
			token.setId(90L);
			return token;
		});
		when(userRoleRepository.findByUserIdOrderByRoleIdAsc(any())).thenReturn(List.of());
		when(lookupService.validateCode(eq("OTP_PURPOSE"), any(), eq("Purpose")))
				.thenAnswer(invocation -> invocation.<String>getArgument(1).trim());
		authService = new NexHomeAuthServiceImpl(otpRequestRepository, refreshTokenRepository, userRepository,
				userRoleRepository, roleRepository, lookupService, permissionService, new NexHomeAuthOtpRequestMapper(),
				new NexHomeAuthRefreshTokenMapper(), new NexHomeUserMapper(), passwordEncoder,
				new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), new NexHomeOtpProperties(6, 5, 3, 60, false),
				new NexHomeSecurityProperties("unused", "nexhome", 15, 30, List.of(), "unused"),
				customerProfileRepository, new NexHomeCustomerProfileMapper(), mock(NexHomePartnerProfileRepository.class),
				mock(NexHomePartnerProfileMapper.class));
	}

	@Test
	void registerCreatesOwnAccountAndSendsRegistrationOtp() {
		NexHomeRole customer = new NexHomeRole();
		customer.setId((short) 4);
		customer.setCode("CUSTOMER");
		when(roleRepository.findByCode("CUSTOMER")).thenReturn(Optional.of(customer));
		when(userRepository.save(any())).thenAnswer(invocation -> {
			NexHomeUser user = invocation.getArgument(0);
			user.setId(10L);
			return user;
		});

		NexHomeAuthOtpResponse response = authService.register(
				new NexHomeAuthRegisterRequest(CC, MOBILE, " New@Example.com ", "CUSTOMER"));

		ArgumentCaptor<NexHomeUser> user = ArgumentCaptor.forClass(NexHomeUser.class);
		verify(userRepository).save(user.capture());
		assertThat(user.getValue().getEmail()).isEqualTo("new@example.com");
		assertThat(user.getValue().getCreatedBy()).isEqualTo(10L);
		verify(userRoleRepository).save(org.mockito.ArgumentMatchers.argThat(userRole ->
				userRole.getRole() == customer && userRole.getAssignedBy().equals(10L)));
		// A customer gets the (empty) customer profile with its customer id at sign-up
		verify(customerProfileRepository).save(org.mockito.ArgumentMatchers.argThat(profile ->
				profile.getUser() == user.getValue() && profile.getFirstName() == null));
		assertThat(response.purpose()).isEqualTo("REGISTRATION");
		assertThat(response.tokens()).isNull();
	}

	@Test
	void registerRejectsExistingNumber() {
		existingUser();

		assertThatThrownBy(() -> authService.register(new NexHomeAuthRegisterRequest(CC, MOBILE, null, "PARTNER")))
				.isInstanceOf(DuplicateResourceException.class)
				.hasMessage("An account already exists with this mobile number. Log in instead.");
	}

	@Test
	void loginOtpIsStoredOnlyAsHash() {
		NexHomeUser user = existingUser();

		NexHomeAuthOtpResponse response = authService.requestOtp(new NexHomeAuthOtpCreateRequest(CC, MOBILE,
				"LOGIN"));

		ArgumentCaptor<NexHomeAuthOtpRequest> saved = ArgumentCaptor.forClass(NexHomeAuthOtpRequest.class);
		verify(otpRequestRepository).save(saved.capture());
		assertThat(saved.getValue().getUser()).isSameAs(user);
		assertThat(saved.getValue().getOtpHash()).startsWith("$2a$");
		assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(240));
		assertThat(response.requestStatus()).isEqualTo("PENDING");
		assertThat(response.user()).isNull();
	}

	@Test
	void otpRequestChecksAccount() {
		assertThatThrownBy(() -> authService.requestOtp(new NexHomeAuthOtpCreateRequest(CC, MOBILE, "LOGIN")))
				.isInstanceOf(ResourceNotFoundException.class);

		NexHomeUser user = existingUser();
		user.setAccountVerifiedDate(Instant.now());
		assertThatThrownBy(() -> authService.requestOtp(new NexHomeAuthOtpCreateRequest(CC, MOBILE,
				"REGISTRATION")))
				.hasMessage("This mobile number is already verified. Log in instead.");

		user.setAccountStatus("BLOCKED");
		assertThatThrownBy(() -> authService.requestOtp(new NexHomeAuthOtpCreateRequest(CC, MOBILE, "LOGIN")))
				.hasMessage("This account is not active.");
		verify(otpRequestRepository, never()).save(any());
	}

	@Test
	void adminOtpIsSentOnlyToActiveStaff() {
		NexHomeAuthAdminOtpRequest request = new NexHomeAuthAdminOtpRequest(CC, MOBILE);
		String invalidUser = "Invalid user. This mobile number does not have admin access.";

		// Unknown number
		assertThatThrownBy(() -> authService.requestAdminOtp(request))
				.isInstanceOf(ResourceNotFoundException.class).hasMessage(invalidUser);

		// Customer or partner account
		NexHomeUser user = existingUser();
		assertThatThrownBy(() -> authService.requestAdminOtp(request))
				.isInstanceOf(ResourceNotFoundException.class).hasMessage(invalidUser);

		// Staff account that is not active: the same answer
		when(userRoleRepository.existsByUserIdAndRoleCodeInAndActiveTrueAndRoleActiveFlagTrue(eq(10L),
				eq(List.of("SUPER_USER", "ADMIN", "SUB_ADMIN")))).thenReturn(true);
		user.setAccountStatus("BLOCKED");
		assertThatThrownBy(() -> authService.requestAdminOtp(request))
				.isInstanceOf(ResourceNotFoundException.class).hasMessage(invalidUser);
		verify(otpRequestRepository, never()).save(any());

		user.setAccountStatus("ACTIVE");
		NexHomeAuthOtpResponse response = authService.requestAdminOtp(request);

		ArgumentCaptor<NexHomeAuthOtpRequest> saved = ArgumentCaptor.forClass(NexHomeAuthOtpRequest.class);
		verify(otpRequestRepository).save(saved.capture());
		assertThat(saved.getValue().getUser()).isSameAs(user);
		assertThat(saved.getValue().getPurpose()).isEqualTo("LOGIN");
		assertThat(response.requestStatus()).isEqualTo("PENDING");
	}

	@Test
	void phoneChangeAndBookingPurposesAreRejectedOnPublicEndpoints() {
		assertThatThrownBy(() -> authService.requestOtp(new NexHomeAuthOtpCreateRequest(CC, MOBILE,
				"PHONE_CHANGE")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("Use the phone change endpoints to change a mobile number.");
		assertThatThrownBy(() -> authService.requestOtp(new NexHomeAuthOtpCreateRequest(CC, MOBILE,
				"SERVICE_START")))
				.hasMessage("Booking OTPs are not available yet.");
	}

	@Test
	void resendWithinCooldownIsRejected() {
		existingUser();
		NexHomeAuthOtpRequest latest = new NexHomeAuthOtpRequest();
		latest.setCreationDate(Instant.now().minusSeconds(10));
		when(otpRequestRepository.findFirstByCountryCodeAndMobileNumberAndPurposeOrderByCreationDateDesc(CC, MOBILE,
				"LOGIN")).thenReturn(Optional.of(latest));

		assertThatThrownBy(() -> authService.requestOtp(new NexHomeAuthOtpCreateRequest(CC, MOBILE, "LOGIN")))
				.isInstanceOf(TooManyRequestsException.class)
				.hasMessage("Please wait 50 seconds before requesting another OTP.");
	}

	@Test
	void newOtpExpiresPendingOneFirst() {
		NexHomeUser user = existingUser();
		NexHomeAuthOtpRequest previous = pendingOtp(user, "LOGIN", "111111", CC, MOBILE);
		previous.setCreationDate(Instant.now().minusSeconds(120));

		authService.requestOtp(new NexHomeAuthOtpCreateRequest(CC, MOBILE, "LOGIN"));

		assertThat(previous.getRequestStatus()).isEqualTo("EXPIRED");
		InOrder order = inOrder(otpRequestRepository);
		order.verify(otpRequestRepository).saveAndFlush(previous);
		order.verify(otpRequestRepository).save(any());
	}

	@Test
	void verifiedLoginStartsSessionWithSignedAccessToken() {
		NexHomeUser user = existingUser();
		NexHomeAuthOtpRequest otpRequest = pendingOtp(user, "LOGIN", "123456", CC, MOBILE);

		NexHomeAuthOtpResponse response = authService.verifyOtp(
				new NexHomeAuthOtpVerifyRequest(CC, MOBILE, "LOGIN", "123456"), "JUnit");

		assertThat(otpRequest.getRequestStatus()).isEqualTo("VERIFIED");
		assertThat(user.getLastLoginAt()).isNotNull();
		assertThat(user.getAccountVerifiedDate()).isNotNull();
		NexHomeAuthTokenResponse tokens = response.tokens();
		assertThat(tokens.tokenType()).isEqualTo("Bearer");

		Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build()
				.decode(tokens.accessToken());
		assertThat(jwt.getSubject()).isEqualTo("10");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("nexhome");
		assertThat(jwt.getExpiresAt()).isEqualTo(tokens.accessTokenExpiresAt());

		ArgumentCaptor<NexHomeAuthRefreshToken> stored = ArgumentCaptor.forClass(NexHomeAuthRefreshToken.class);
		verify(refreshTokenRepository).save(stored.capture());
		// The access token belongs to the stored session, so ending the session ends the access token
		assertThat(stored.getValue().getSessionId()).isNotNull();
		assertThat(jwt.getClaimAsString("sid")).isEqualTo(stored.getValue().getSessionId().toString());
		assertThat(stored.getValue().getTokenHash()).hasSize(64).isNotEqualTo(tokens.refreshToken());
		assertThat(stored.getValue().getUserAgent()).isEqualTo("JUnit");
		assertThat(tokens.toString()).doesNotContain(tokens.accessToken()).doesNotContain(tokens.refreshToken());
	}

	@Test
	void wrongOtpCountsAttemptsAndLocks() {
		NexHomeAuthOtpRequest otpRequest = pendingOtp(existingUser(), "LOGIN", "123456", CC, MOBILE);
		NexHomeAuthOtpVerifyRequest wrong = new NexHomeAuthOtpVerifyRequest(CC, MOBILE, "LOGIN", "000000");

		assertThatThrownBy(() -> authService.verifyOtp(wrong, null)).hasMessage("OTP is incorrect.");
		assertThatThrownBy(() -> authService.verifyOtp(wrong, null)).hasMessage("OTP is incorrect.");
		assertThatThrownBy(() -> authService.verifyOtp(wrong, null))
				.hasMessage("Too many wrong attempts. Request a new OTP.");
		assertThat(otpRequest.getRequestStatus()).isEqualTo("LOCKED");
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void expiredOtpIsRejected() {
		NexHomeAuthOtpRequest otpRequest = pendingOtp(existingUser(), "LOGIN", "123456", CC, MOBILE);
		otpRequest.setExpiresAt(Instant.now().minusSeconds(1));

		assertThatThrownBy(() -> authService.verifyOtp(
				new NexHomeAuthOtpVerifyRequest(CC, MOBILE, "LOGIN", "123456"), null))
				.hasMessage("OTP has expired. Request a new one.");
		assertThat(otpRequest.getRequestStatus()).isEqualTo("EXPIRED");
	}

	@Test
	void refreshRotatesToken() {
		NexHomeUser user = existingUser();
		NexHomeAuthRefreshToken current = storedRefreshToken(user, "old-token");

		NexHomeAuthTokenResponse tokens = authService.refreshTokens(
				new NexHomeAuthRefreshTokenRequest("old-token"), null);

		assertThat(tokens.refreshToken()).isNotEqualTo("old-token");
		assertThat(current.getRevokedReason()).isEqualTo("ROTATED");
		assertThat(current.getReplacedBy().getId()).isEqualTo(90L);
		// Rotation continues the same login session
		assertThat(current.getReplacedBy().getSessionId()).isEqualTo(SESSION_ID);
		assertThat(NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build()
				.decode(tokens.accessToken()).getClaimAsString("sid")).isEqualTo(SESSION_ID.toString());
		assertThat(current.getLastUsedAt()).isNotNull();
	}

	@Test
	void reusedRefreshTokenEndsAllSessions() {
		NexHomeUser user = existingUser();
		NexHomeAuthRefreshToken current = storedRefreshToken(user, "old-token");
		current.setRevokedAt(Instant.now());
		current.setRevokedReason("ROTATED");

		assertThatThrownBy(() -> authService.refreshTokens(new NexHomeAuthRefreshTokenRequest("old-token"), null))
				.isInstanceOf(UnauthorizedException.class);
		verify(refreshTokenRepository).revokeAllByUserId(eq(10L), eq("REUSE_DETECTED"), any(), eq(10L));
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void unknownExpiredOrInactiveRefreshIsRejected() {
		assertThatThrownBy(() -> authService.refreshTokens(new NexHomeAuthRefreshTokenRequest("nope"), null))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessage("The refresh token is invalid or has expired.");

		NexHomeUser user = existingUser();
		NexHomeAuthRefreshToken expired = storedRefreshToken(user, "expired");
		expired.setExpiresAt(Instant.now().minusSeconds(1));
		assertThatThrownBy(() -> authService.refreshTokens(new NexHomeAuthRefreshTokenRequest("expired"), null))
				.isInstanceOf(UnauthorizedException.class);

		storedRefreshToken(user, "blocked");
		user.setAccountStatus("BLOCKED");
		assertThatThrownBy(() -> authService.refreshTokens(new NexHomeAuthRefreshTokenRequest("blocked"), null))
				.hasMessage("This account is not active.");
		verify(refreshTokenRepository).revokeAllByUserId(eq(10L), eq("ACCOUNT_STATUS_CHANGED"), any(), eq(10L));
	}

	@Test
	void logoutRevokesOnlyThatSession() {
		NexHomeAuthRefreshToken token = storedRefreshToken(existingUser(), "token");

		authService.logout(new NexHomeAuthRefreshTokenRequest("token"));
		authService.logout(new NexHomeAuthRefreshTokenRequest("unknown"));

		assertThat(token.getRevokedReason()).isEqualTo("LOGOUT");
		verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any(), any(), any());
	}

	@Test
	void phoneChangeIsForSignedInUserOnly() {
		NexHomeUser user = existingUser();
		when(permissionService.currentUserId()).thenReturn(10L);
		when(userRepository.findById(10L)).thenReturn(Optional.of(user));

		authService.requestPhoneChangeOtp(new NexHomeAuthPhoneChangeRequest(CC, "9000000000"));
		ArgumentCaptor<NexHomeAuthOtpRequest> saved = ArgumentCaptor.forClass(NexHomeAuthOtpRequest.class);
		verify(otpRequestRepository).save(saved.capture());
		assertThat(saved.getValue().getUser()).isSameAs(user);
		assertThat(saved.getValue().getMobileNumber()).isEqualTo("9000000000");

		// Someone else's pending OTP for the number cannot be used
		NexHomeUser other = new NexHomeUserMapper().toUserEntity(CC, "9111111111", null, null, 1L);
		other.setId(11L);
		pendingOtp(other, "PHONE_CHANGE", "123456", CC, "9000000000");
		assertThatThrownBy(() -> authService.verifyPhoneChangeOtp(
				new NexHomeAuthPhoneChangeVerifyRequest(CC, "9000000000", "123456")))
				.hasMessage("No pending OTP for this mobile number. Request a new one.");

		pendingOtp(user, "PHONE_CHANGE", "123456", CC, "9000000000");
		NexHomeAuthOtpResponse response = authService.verifyPhoneChangeOtp(
				new NexHomeAuthPhoneChangeVerifyRequest(CC, "9000000000", "123456"));
		assertThat(user.getMobileNumber()).isEqualTo("9000000000");
		assertThat(response.user().mobileNumber()).isEqualTo("9000000000");
	}

	@Test
	void failedAttemptsAndReuseRevocationsAreCommitted() throws NoSuchMethodException {
		assertThat(NexHomeAuthServiceImpl.class.getMethod("verifyOtp", NexHomeAuthOtpVerifyRequest.class,
				String.class).getAnnotation(Transactional.class).noRollbackFor())
				.containsExactly(InvalidRequestException.class);
		assertThat(NexHomeAuthServiceImpl.class.getMethod("refreshTokens", NexHomeAuthRefreshTokenRequest.class,
				String.class).getAnnotation(Transactional.class).noRollbackFor())
				.containsExactly(UnauthorizedException.class);
	}

	@Test
	void requestsNeverPrintSecrets() {
		assertThat(new NexHomeAuthOtpVerifyRequest(CC, MOBILE, "LOGIN", "123456").toString()).doesNotContain("123456");
		assertThat(new NexHomeAuthRefreshTokenRequest("secret-token").toString()).doesNotContain("secret-token");
	}

	private NexHomeUser existingUser() {
		NexHomeUser user = new NexHomeUserMapper().toUserEntity(CC, MOBILE, null, null, 1L);
		user.setId(10L);
		when(userRepository.findByCountryCodeAndMobileNumber(CC, MOBILE)).thenReturn(Optional.of(user));
		return user;
	}

	private NexHomeAuthOtpRequest pendingOtp(NexHomeUser user, String purpose, String otp, String countryCode,
			String mobileNumber) {
		NexHomeAuthOtpRequest otpRequest = new NexHomeAuthOtpRequestMapper().toAuthOtpRequestEntity(countryCode,
				mobileNumber, purpose, user, passwordEncoder.encode(otp), Instant.now().plusSeconds(300), user.getId());
		otpRequest.setId(69L);
		when(otpRequestRepository.findByCountryCodeAndMobileNumberAndPurposeAndRequestStatus(countryCode, mobileNumber,
				purpose, "PENDING")).thenAnswer(invocation -> "PENDING".equals(otpRequest.getRequestStatus())
						? Optional.of(otpRequest) : Optional.empty());
		return otpRequest;
	}

	/** Stores a session whose token is {@code token}, looked up by its SHA-256 like the service does. */
	private NexHomeAuthRefreshToken storedRefreshToken(NexHomeUser user, String token) {
		NexHomeAuthRefreshToken stored = new NexHomeAuthRefreshTokenMapper().toRefreshTokenEntity(user, SESSION_ID,
				sha256(token), Instant.now().plusSeconds(3600), null);
		stored.setId(80L);
		when(refreshTokenRepository.findByTokenHash(sha256(token))).thenReturn(Optional.of(stored));
		return stored;
	}

	private static String sha256(String value) {
		try {
			return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
