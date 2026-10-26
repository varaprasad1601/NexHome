package com.nexhome.app.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One version of a policy, e.g. TERMS_OF_SERVICE 2.0. A published version never changes (history is kept for the
 * acceptances that refer to it); a new version is a new row. {@code (policy_key, version)} is unique.
 */
@Entity
@Table(name = "nexhome_policy_documents")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePolicyDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// The key and version identify the document and are fixed once it is created
	@Column(name = "policy_key", nullable = false, updatable = false, length = 80)
	private String policyKey;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	/** POLICY_AUDIENCE lookup code: CUSTOMER, PARTNER or BOTH. */
	@Column(name = "audience", nullable = false, length = 60)
	private String audience;

	@Column(name = "version", nullable = false, updatable = false, length = 30)
	private String version;

	@Column(name = "content", nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "effective_from", nullable = false)
	private Instant effectiveFrom;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "is_published", nullable = false)
	private boolean published = false;

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
