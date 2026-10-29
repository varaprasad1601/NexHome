package com.nexhome.app.model;

import java.math.BigDecimal;
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
 * An attempt to send a payout to the partner's bank account; retried with the same idempotency key.
 */
@Entity
@Table(name = "nexhome_partner_transfers")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePartnerTransfer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payout_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomePartnerPayout payout;

	@Column(name = "provider_transfer_id", length = 150)
	private String providerTransferId;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	/** TRANSFER_STATUS lookup code. */
	@Column(name = "status", nullable = false, length = 60)
	private String status;

	@Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
	private String idempotencyKey;

	/** The provider's answer, without secrets (JSON). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "provider_response", columnDefinition = "jsonb")
	private String providerResponse;

	@Column(name = "settled_at")
	private Instant settledAt;

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
