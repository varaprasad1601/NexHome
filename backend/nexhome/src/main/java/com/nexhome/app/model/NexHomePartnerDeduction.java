package com.nexhome.app.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
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
 * An amount NexHome keeps from what it owes a partner (V63): DEDUCTION_REASON (a penalty, damage, ...), notes
 * (required for OTHER), the day, and the payout it was recorded with, if any. It reduces what NexHome owes; one
 * recorded by mistake is CANCELLED with a reason, never deleted.
 */
@Entity
@Table(name = "nexhome_partner_deductions")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePartnerDeduction {

	public static final String RECORDED = "RECORDED";
	public static final String CANCELLED = "CANCELLED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Generated
	@Column(name = "public_id", nullable = false, insertable = false, updatable = false, length = 24)
	private String publicId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "partner_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomePartnerProfile partner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payout_id", referencedColumnName = "id", updatable = false)
	private NexHomePartnerManualPayout payout;

	@Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "deduction_reason", nullable = false, updatable = false, length = 60)
	private String deductionReason;

	@Column(name = "notes", updatable = false, length = 500)
	private String notes;

	@Column(name = "deducted_on", nullable = false, updatable = false)
	private LocalDate deductedOn;

	@Column(name = "deduction_status", nullable = false, length = 20)
	private String deductionStatus = RECORDED;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "cancelled_by")
	private Long cancelledBy;

	@Column(name = "cancellation_reason", length = 200)
	private String cancellationReason;

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
