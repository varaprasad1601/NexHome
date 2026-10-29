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
 * One entry of the security and business audit history (Module 9): who did what to which record, with the values
 * before and after (JSON) and the client's address. Never holds OTPs, tokens, bank details or document contents.
 */
@Entity
@Table(name = "nexhome_audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeAuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_user_id", referencedColumnName = "id", updatable = false)
	private NexHomeUser actor;

	@Column(name = "action", nullable = false, length = 100, updatable = false)
	private String action;

	@Column(name = "entity_type", nullable = false, length = 60, updatable = false)
	private String entityType;

	@Column(name = "entity_id", nullable = false, length = 100, updatable = false)
	private String entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "old_values", columnDefinition = "jsonb", updatable = false)
	private String oldValues;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "new_values", columnDefinition = "jsonb", updatable = false)
	private String newValues;

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
