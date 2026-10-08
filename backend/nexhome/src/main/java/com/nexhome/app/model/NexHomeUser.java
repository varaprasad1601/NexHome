package com.nexhome.app.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "nexhome_users")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long userId;

	// Phone and role identify the user (phone + role is unique per user) and are fixed once the user is created
	@Column(name = "phone", nullable = false, updatable = false, length = 10)
	private String phone;

	/** Store a hash (e.g. BCrypt), never the raw password. */
	// Null until the approved user sets it (see NexHomeUserStatus)
	@Column(name = "password")
	private String password;

	@Column(name = "email", length = 254)
	private String email;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id", referencedColumnName = "role_id", nullable = false, updatable = false)
	private NexHomeRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_status", nullable = false, length = 20)
	private NexHomeUserStatus userStatus = NexHomeUserStatus.SUBMITTED;

	// Only an ACTIVATED user (who has set a password) can be active and access the application
	@Column(name = "active_flag", nullable = false)
	private boolean activeFlag = false;

	@Column(name = "last_login")
	private Instant lastLogin;

	@CreationTimestamp
	@Column(name = "creation_date", nullable = false, updatable = false)
	private Instant creationDate;

	@Column(name = "created_by", updatable = false)
	private Long createdBy;

	@UpdateTimestamp
	@Column(name = "last_update_date", nullable = false)
	private Instant lastUpdateDate;

	@Column(name = "last_update_by")
	private Long lastUpdateBy;

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
