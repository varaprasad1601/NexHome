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
 * A frequently asked question: general, or for one category or one service (never both, check constraint
 * {@code ck_nexhome_faqs_scope}).
 */
@Entity
@Table(name = "nexhome_faqs")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeFaq {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "question", nullable = false, length = 500)
	private String question;

	@Column(name = "answer", nullable = false, columnDefinition = "text")
	private String answer;

	// The scope is fixed once the FAQ is created
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", referencedColumnName = "id", updatable = false)
	private NexHomeServiceCategory category;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "service_id", referencedColumnName = "id", updatable = false)
	private NexHomeService service;

	/** FAQ_AUDIENCE lookup code: CUSTOMER, PARTNER or BOTH. */
	@Column(name = "audience", nullable = false, length = 60)
	private String audience;

	@Column(name = "display_order", nullable = false)
	private int displayOrder = 0;

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
