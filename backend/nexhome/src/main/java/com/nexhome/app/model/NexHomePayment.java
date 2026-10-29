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
 * What was captured (or failed) for one booking of a payment order.
 */
@Entity
@Table(name = "nexhome_payments")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePayment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_order_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomePaymentOrder paymentOrder;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeBooking booking;

	@Column(name = "provider", nullable = false, updatable = false, length = 30)
	private String provider;

	@Column(name = "provider_payment_id", length = 150)
	private String providerPaymentId;

	/** PAYMENT_METHOD lookup code. */
	@Column(name = "payment_method", nullable = false, length = 60)
	private String paymentMethod;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "INR";

	/** PAYMENT_STATUS lookup code. */
	@Column(name = "status", nullable = false, length = 60)
	private String status;

	@Column(name = "failure_code", length = 100)
	private String failureCode;

	@Column(name = "failure_message", columnDefinition = "text")
	private String failureMessage;

	@Column(name = "captured_at")
	private Instant capturedAt;

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
