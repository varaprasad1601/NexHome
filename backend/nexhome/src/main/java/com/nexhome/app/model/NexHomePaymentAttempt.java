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
 * One attempt to pay a payment order at the gateway (V67): successful, failed, pending, cancelled or dropped, as the
 * gateway reports it.
 */
@Entity
@Table(name = "nexhome_payment_attempts")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePaymentAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_order_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomePaymentOrder paymentOrder;

	@Column(name = "provider", nullable = false, updatable = false, length = 30)
	private String provider;

	@Column(name = "provider_payment_id", nullable = false, updatable = false, length = 150)
	private String providerPaymentId;

	/** The gateway's status: SUCCESS, FAILED, PENDING, USER_DROPPED, CANCELLED, VOID, NOT_ATTEMPTED. */
	@Column(name = "attempt_status", nullable = false, length = 40)
	private String attemptStatus;

	@Column(name = "payment_mode", length = 40)
	private String paymentMode;

	@Column(name = "payment_method_detail", length = 200)
	private String paymentMethodDetail;

	@Column(name = "amount", precision = 12, scale = 2)
	private BigDecimal amount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "INR";

	@Column(name = "bank_reference", length = 150)
	private String bankReference;

	@Column(name = "error_code", length = 100)
	private String errorCode;

	@Column(name = "message", columnDefinition = "text")
	private String message;

	@Column(name = "attempted_at")
	private Instant attemptedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	/** What the gateway charged the customer on top (V69, Cashfree's payment_surcharge): charge and its GST. */
	@Column(name = "surcharge_amount", precision = 12, scale = 2)
	private BigDecimal surchargeAmount;

	@Column(name = "surcharge_tax", precision = 12, scale = 2)
	private BigDecimal surchargeTax;

	/** The gateway's charge on a successful attempt (V68; Cashfree's service_charge), null until it is known. */
	@Column(name = "gateway_charge", precision = 12, scale = 2)
	private BigDecimal gatewayCharge;

	/** The tax (GST) on that charge (Cashfree's service_tax). */
	@Column(name = "gateway_tax", precision = 12, scale = 2)
	private BigDecimal gatewayTax;

	/** What the gateway pays NexHome for it: amount - charge - tax. */
	@Column(name = "settlement_amount", precision = 12, scale = 2)
	private BigDecimal settlementAmount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "settlement_currency", length = 3)
	private String settlementCurrency;

	/** The gateway's settlement (Cashfree's cf_settlement_id), its bank transfer's UTR and time. */
	@Column(name = "settlement_id", length = 100)
	private String settlementId;

	@Column(name = "settlement_utr", length = 100)
	private String settlementUtr;

	@Column(name = "settled_at")
	private Instant settledAt;

	/** When the charges were recorded (set with them). */
	@Column(name = "charges_recorded_at")
	private Instant chargesRecordedAt;

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
