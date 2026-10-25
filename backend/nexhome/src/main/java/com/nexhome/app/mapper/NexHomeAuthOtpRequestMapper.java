package com.nexhome.app.mapper;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeAuthOtpResponse;
import com.nexhome.app.dto.NexHomeAuthTokenResponse;
import com.nexhome.app.dto.NexHomeUserResponse;
import com.nexhome.app.model.NexHomeAuthOtpRequest;
import com.nexhome.app.model.NexHomeLookupCodes.OtpRequestStatus;
import com.nexhome.app.model.NexHomeUser;

@Component
public class NexHomeAuthOtpRequestMapper {

	/** A new OTP is PENDING with no attempts. Only the hash is stored, never the OTP. */
	public NexHomeAuthOtpRequest toAuthOtpRequestEntity(String countryCode, String mobileNumber, String purpose,
			NexHomeUser user, String otpHash, Instant expiresAt, Long actingUserId) {
		NexHomeAuthOtpRequest otpRequest = new NexHomeAuthOtpRequest();
		otpRequest.setUser(user);
		otpRequest.setCountryCode(countryCode);
		otpRequest.setMobileNumber(mobileNumber);
		otpRequest.setPurpose(purpose);
		otpRequest.setOtpHash(otpHash);
		otpRequest.setRequestStatus(OtpRequestStatus.PENDING);
		otpRequest.setAttemptCount((short) 0);
		otpRequest.setExpiresAt(expiresAt);
		otpRequest.setCreatedBy(actingUserId);
		otpRequest.setUpdatedBy(actingUserId);
		return otpRequest;
	}

	public NexHomeAuthOtpResponse toAuthOtpDto(NexHomeAuthOtpRequest otpRequest, NexHomeUserResponse user,
			NexHomeAuthTokenResponse tokens) {
		return new NexHomeAuthOtpResponse(
				otpRequest.getId(),
				otpRequest.getCountryCode(),
				otpRequest.getMobileNumber(),
				otpRequest.getPurpose(),
				otpRequest.getRequestStatus(),
				otpRequest.getExpiresAt(),
				otpRequest.getVerifiedAt(),
				user,
				tokens);
	}
}
