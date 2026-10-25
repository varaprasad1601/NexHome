package com.nexhome.app.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shared account and login data. Names live in the profile tables, and roles are assigned through
 * {@link NexHomeUserRole}. Users sign in with an OTP sent to their mobile number; there is no password.
 * <p>
 * Email is unique case-insensitively when present, enforced by the index {@code ux_nexhome_users_email}.
 */
@Entity
@Table(name = "nexhome_users",
		uniqueConstraints = @UniqueConstraint(columnNames = { "country_code", "mobile_number" }))
@Getter
@Setter
@NoArgsConstructor
public class NexHomeUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// The mobile number only changes through a verified PHONE_CHANGE OTP
	@Column(name = "country_code", nullable = false, length = 5)
	private String countryCode;

	@Column(name = "mobile_number", nullable = false, length = 15)
	private String mobileNumber;

	@Column(name = "email")
	private String email;

	@Column(name = "profile_photo_url", columnDefinition = "text")
	private String profilePhotoUrl;

	/** ACCOUNT_STATUS lookup code. */
	@Column(name = "account_status", nullable = false, length = 60)
	private String accountStatus;

	// When the user first proved they own the mobile number with an OTP
	@Column(name = "account_verified_date")
	private Instant accountVerifiedDate;

	@Column(name = "status_updated_by")
	private Long statusUpdatedBy;

	@Column(name = "status_updated_date")
	private Instant statusUpdatedDate;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

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
