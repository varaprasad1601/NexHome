package com.nexhome.app.model;

import java.time.Instant;

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
 * A partner's payout bank account. The account number is stored encrypted, with its last four digits in clear for
 * display. At most one account per partner is primary, enforced by the partial unique index
 * {@code ux_nexhome_partner_bank_accounts_one_primary}.
 */
@Entity
@Table(name = "nexhome_partner_bank_accounts")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePartnerBankAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// The partner is fixed once the record is created
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "partner_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomePartnerProfile partner;

	@Column(name = "account_holder_name", nullable = false, length = 150)
	private String accountHolderName;

	// The account number and IFSC identify the account and are fixed once it is added; the number is encrypted with
	// NexHomeFieldEncryptor and never logged or returned
	@Column(name = "account_number_encrypted", nullable = false, updatable = false, columnDefinition = "text")
	private String accountNumberEncrypted;

	@Column(name = "account_number_last4", nullable = false, updatable = false, length = 4)
	private String accountNumberLast4;

	@Column(name = "ifsc_code", nullable = false, updatable = false, length = 11)
	private String ifscCode;

	@Column(name = "bank_name", length = 150)
	private String bankName;

	/** The branch, e.g. "Madhapur" (V23). */
	@Column(name = "branch_name", length = 150)
	private String branchName;

	/** VERIFICATION_STATUS lookup code. */
	@Column(name = "verification_status", nullable = false, length = 60)
	private String verificationStatus;

	/** Why staff rejected the account (V27); only while REJECTED. */
	@Column(name = "rejection_reason", columnDefinition = "text")
	private String rejectionReason;

	/** Set by the payout integration (Module 7). */
	@Column(name = "gateway_beneficiary_id", length = 150)
	private String gatewayBeneficiaryId;

	@Column(name = "is_primary", nullable = false)
	private boolean primary = true;

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
