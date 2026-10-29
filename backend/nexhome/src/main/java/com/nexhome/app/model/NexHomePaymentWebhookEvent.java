package com.nexhome.app.model;

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
 * A provider's event, stored before it is processed; (provider, provider event id) is unique, so a repeated
 * delivery is processed once.
 */
@Entity
@Table(name = "nexhome_payment_webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePaymentWebhookEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "provider", nullable = false, updatable = false, length = 30)
	private String provider;

	@Column(name = "provider_event_id", nullable = false, updatable = false, length = 150)
	private String providerEventId;

	@Column(name = "event_type", nullable = false, updatable = false, length = 100)
	private String eventType;

	/** The event as received (JSON). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
	private String payloadJson;

	@Column(name = "signature_verified", nullable = false)
	private boolean signatureVerified;

	/** WEBHOOK_PROCESSING_STATUS lookup code. */
	@Column(name = "processing_status", nullable = false, length = 60)
	private String processingStatus;

	@Column(name = "received_at", nullable = false, updatable = false)
	private Instant receivedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	/** Not in the design: why processing failed or the event was ignored. */
	@Column(name = "processing_error", columnDefinition = "text")
	private String processingError;

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
