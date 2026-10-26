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
 * A prerequisite or instruction for the customer of a service, e.g. "Keep the area clear".
 */
@Entity
@Table(name = "nexhome_service_requirements")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeServiceRequirement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// The service is fixed once the requirement is added
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "service_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeService service;

	/** REQUIREMENT_TYPE lookup code. */
	@Column(name = "requirement_type", nullable = false, length = 30)
	private String requirementType;

	@Column(name = "description", nullable = false, columnDefinition = "text")
	private String description;

	@Column(name = "is_mandatory", nullable = false)
	private boolean mandatory = true;

	@Column(name = "display_order", nullable = false)
	private int displayOrder = 0;

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
