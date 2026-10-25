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
 * Role definition. SUPER_USER, ADMIN, SUB_ADMIN, CUSTOMER and PARTNER are seeded by Flyway.
 */
@Entity
@Table(name = "nexhome_roles")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeRole {

	/** Has every permission without role functions. */
	public static final String SUPER_USER = "SUPER_USER";
	public static final String ADMIN = "ADMIN";
	public static final String SUB_ADMIN = "SUB_ADMIN";
	public static final String CUSTOMER = "CUSTOMER";
	public static final String PARTNER = "PARTNER";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Short id;

	// The code identifies the role and is fixed once it is created
	@Column(name = "code", nullable = false, unique = true, updatable = false, length = 30)
	private String code;

	@Column(name = "name", nullable = false, length = 80)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "active_flag", nullable = false)
	private boolean activeFlag = true;

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
