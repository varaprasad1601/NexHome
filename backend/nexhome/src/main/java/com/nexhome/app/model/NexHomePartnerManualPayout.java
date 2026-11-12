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
 * A payment to a partner made outside the app and recorded by staff (V62): how (PAYOUT_MODE), the transaction id,
 * the amount and the day. It reduces what NexHome owes the partner; a payout recorded by mistake is CANCELLED with a
 * reason, never deleted.
 */
@Entity
@Table(name = "nexhome_partner_manual_payouts")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePartnerManualPayout {

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

	@Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "payment_mode", nullable = false, updatable = false, length = 60)
	private String paymentMode;

	@Column(name = "transaction_id", updatable = false, length = 100)
	private String transactionId;

	@Column(name = "paid_on", nullable = false, updatable = false)
	private LocalDate paidOn;

	@Column(name = "notes", updatable = false, length = 500)
	private String notes;

	@Column(name = "payout_status", nullable = false, length = 20)
	private String payoutStatus = RECORDED;

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
