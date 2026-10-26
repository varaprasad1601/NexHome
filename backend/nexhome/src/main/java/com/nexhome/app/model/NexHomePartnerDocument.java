package com.nexhome.app.model;

import java.time.Instant;
import java.time.LocalDate;

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
 * An identity or certification document of a partner. The file itself is in protected object storage; only its key
 * is stored here.
 */
@Entity
@Table(name = "nexhome_partner_documents")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePartnerDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// The partner is fixed once the record is created
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "partner_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomePartnerProfile partner;

	/** DOCUMENT_TYPE lookup code. */
	@Column(name = "document_type", nullable = false, updatable = false, length = 60)
	private String documentType;

	/** Encrypted with {@code NexHomeFieldEncryptor}; never logged or returned unmasked. */
	@Column(name = "document_number_encrypted", columnDefinition = "text")
	private String documentNumberEncrypted;

	@Column(name = "file_storage_key", nullable = false, columnDefinition = "text")
	private String fileStorageKey;

	/** VERIFICATION_STATUS lookup code. */
	@Column(name = "verification_status", nullable = false, length = 60)
	private String verificationStatus;

	@Column(name = "verified_by")
	private Long verifiedBy;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@Column(name = "expiry_date")
	private LocalDate expiryDate;

	@Column(name = "rejection_reason", columnDefinition = "text")
	private String rejectionReason;

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
