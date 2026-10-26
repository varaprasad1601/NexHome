package com.nexhome.app.model;

import java.net.InetAddress;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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
 * A user's acceptance of one policy version; one per user and version.
 */
@Entity
@Table(name = "nexhome_policy_acceptances")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePolicyAcceptance {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// Who accepted which version is fixed once recorded
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeUser user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "policy_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomePolicyDocument policy;

	@Column(name = "accepted_at", nullable = false, updatable = false)
	private Instant acceptedAt;

	/** ACCEPTANCE_METHOD lookup code. */
	@Column(name = "acceptance_method", nullable = false, updatable = false, length = 60)
	private String acceptanceMethod;

	/** The client's address, as seen by the server. */
	@JdbcTypeCode(SqlTypes.INET)
	@Column(name = "ip_address", updatable = false)
	private InetAddress ipAddress;

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
