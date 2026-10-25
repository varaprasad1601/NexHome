package com.nexhome.app.model;

import java.time.Instant;
import java.util.UUID;

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
 * One login session: a long-lived refresh token that is exchanged for new access tokens. Only a SHA-256 hash of the
 * token is stored. A token is used once: refreshing revokes it (ROTATED) and points {@code replacedBy} at its
 * successor.
 */
@Entity
@Table(name = "nexhome_auth_refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeAuthRefreshToken {

	/** Values of {@code revoked_reason}. */
	public static final class RevokedReason {

		public static final String LOGOUT = "LOGOUT";
		public static final String LOGOUT_ALL = "LOGOUT_ALL";
		public static final String ROTATED = "ROTATED";
		public static final String REUSE_DETECTED = "REUSE_DETECTED";
		public static final String ACCOUNT_STATUS_CHANGED = "ACCOUNT_STATUS_CHANGED";

		private RevokedReason() {
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeUser user;

	/**
	 * The login session: shared by every refresh token of one login across rotations, and carried by its access tokens
	 * as the {@code sid} claim. Access tokens are accepted only while the session has an unrevoked refresh token.
	 */
	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;

	/** Hex SHA-256 of the token; the token itself is only ever sent to the client. */
	@Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_reason", length = 30)
	private String revokedReason;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "replaced_by_id", referencedColumnName = "id")
	private NexHomeAuthRefreshToken replacedBy;

	@Column(name = "user_agent", length = 500, updatable = false)
	private String userAgent;

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
