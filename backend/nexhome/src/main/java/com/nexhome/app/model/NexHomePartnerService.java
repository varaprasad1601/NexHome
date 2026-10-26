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
 * A service a partner is qualified and willing to perform. One row per partner and service; at most one primary
 * service per partner (partial unique index {@code ux_nexhome_partner_services_one_primary}). Uses the common audit
 * columns instead of the design's created_at / updated_at.
 */
@Entity
@Table(name = "nexhome_partner_services")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePartnerService {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// The partner and service identify the row and are fixed once it is created
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "partner_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomePartnerProfile partner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "service_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeService service;

	@Column(name = "experience_years")
	private Short experienceYears;

	@Column(name = "is_primary_service", nullable = false)
	private boolean primaryService = false;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

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
