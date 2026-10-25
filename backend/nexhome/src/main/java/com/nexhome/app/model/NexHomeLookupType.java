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
 * A controlled vocabulary / dropdown group, e.g. BOOKING_STATUS. Its values are {@link NexHomeLookupValue}s.
 */
@Entity
@Table(name = "nexhome_lookup_types")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeLookupType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// The type code identifies the type (business code refers to it) and is fixed once it is created
	@Column(name = "type_code", nullable = false, unique = true, updatable = false, length = 80)
	private String typeCode;

	@Column(name = "type_name", nullable = false, length = 120)
	private String typeName;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "active_flag", nullable = false)
	private boolean activeFlag = true;

	// System-managed types are used by application logic, so they and their values cannot be deactivated
	@Column(name = "is_system_managed", nullable = false, updatable = false)
	private boolean systemManaged = true;

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
