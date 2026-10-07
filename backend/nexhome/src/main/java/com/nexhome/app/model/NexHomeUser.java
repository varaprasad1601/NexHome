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

	@Column(name = "phone", nullable = false, length = 10)
	private String phone;

	/** Store a hash (e.g. BCrypt), never the raw password. */
	@Column(name = "password", nullable = false)
	private String password;

	@Column(name = "email", length = 254)
	private String email;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id", referencedColumnName = "role_id", nullable = false)
	private NexHomeRole role;

	@Column(name = "active_flag", nullable = false)
	private boolean activeFlag = true;

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
