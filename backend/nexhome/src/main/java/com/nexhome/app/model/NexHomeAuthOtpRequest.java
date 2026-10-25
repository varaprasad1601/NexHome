package com.nexhome.app.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One OTP sent to a mobile number for a purpose (login, registration, phone change, and later booking start and
 * completion). Only a BCrypt hash of the OTP is stored. Each OTP is single-use, expires quickly and locks after too
 * many wrong attempts.
 * <p>
 * At most one PENDING OTP exists per mobile number and purpose (and booking), enforced by the partial unique index
 * {@code ux_nexhome_auth_otp_requests_one_pending}.
 */
@Entity
@Table(name = "nexhome_auth_otp_requests")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeAuthOtpRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// Null for a mobile number that has no account
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", referencedColumnName = "id", updatable = false)
	private NexHomeUser user;

	// Becomes an association once the bookings table exists
	@Column(name = "booking_id", updatable = false)
	private Long bookingId;

	@Column(name = "country_code", nullable = false, updatable = false, length = 5)
	private String countryCode;

	@Column(name = "mobile_number", nullable = false, updatable = false, length = 15)
	private String mobileNumber;

	/** BCrypt hash; the plain OTP is never stored. */
	@Column(name = "otp_hash", nullable = false, updatable = false, columnDefinition = "text")
	private String otpHash;

	/** OTP_PURPOSE lookup code. */
	@Column(name = "purpose", nullable = false, updatable = false, length = 60)
	private String purpose;

	/** OTP_REQUEST_STATUS lookup code. */
	@Column(name = "request_status", nullable = false, length = 60)
	private String requestStatus;

	@Column(name = "attempt_count", nullable = false)
	private short attemptCount = 0;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@CreationTimestamp
	@Column(name = "creation_date", nullable = false, updatable = false)
	private Instant creationDate;

	@Column(name = "created_by", updatable = false)
	private Long createdBy;

	@UpdateTimestamp
	@Column(name = "updated_date", nullable = false)
	private Instant updatedDate;

	@Column(name = "updated_by")
	private Long updatedBy;

	@Column(name = "attribute1")
	private String attribute1;

	@Column(name = "attribute2")
	private String attribute2;

	@Column(name = "attribute3")
	private String attribute3;

	@Column(name = "attribute4")
	private String attribute4;

	@Column(name = "attribute5")
	private String attribute5;
}
