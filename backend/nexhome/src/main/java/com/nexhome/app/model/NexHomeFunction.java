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
 * A module, screen, feature, API or action that access can be granted to, defined once. Which roles may use it, and
 * how, is in {@link NexHomeRoleFunction}.
 */
@Entity
@Table(name = "nexhome_functions")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeFunction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// Module and code identify the function and are fixed once it is created
	@Column(name = "function_module", nullable = false, updatable = false, length = 50)
	private String functionModule;

	@Column(name = "function_code", nullable = false, unique = true, updatable = false, length = 100)
	private String functionCode;

	@Column(name = "function_name", nullable = false, length = 150)
	private String functionName;

	/** FUNCTION_TYPE lookup code. */
	@Column(name = "function_type", nullable = false, length = 60)
	private String functionType;

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
