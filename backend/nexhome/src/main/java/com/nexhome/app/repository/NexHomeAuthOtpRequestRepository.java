package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeAuthOtpRequest;

@Repository
public interface NexHomeAuthOtpRequestRepository extends JpaRepository<NexHomeAuthOtpRequest, Long> {

	/** The latest OTP of any status, used for the resend cooldown. */
	Optional<NexHomeAuthOtpRequest> findFirstByCountryCodeAndMobileNumberAndPurposeOrderByCreationDateDesc(
			String countryCode, String mobileNumber, String purpose);

	/** The pending OTP; unique by the partial index ux_nexhome_auth_otp_requests_one_pending. */
	Optional<NexHomeAuthOtpRequest> findByCountryCodeAndMobileNumberAndPurposeAndRequestStatus(String countryCode,
			String mobileNumber, String purpose, String requestStatus);
}
