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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Access a role has to a function: read (view data), write (create/edit/save and use action buttons)
 * and delete.
 */
@Entity
@Table(name = "nexhome_role_functions",
		uniqueConstraints = @UniqueConstraint(columnNames = { "role_id", "function_id" }))
@Getter
@Setter
@NoArgsConstructor
public class NexHomeRoleFunction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "role_function_id")
	private Long roleFunctionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	// Role and function identify the row and are fixed once it is created
	@JoinColumn(name = "role_id", referencedColumnName = "role_id", nullable = false, updatable = false)
	private NexHomeRole role;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "function_id", referencedColumnName = "function_id", nullable = false, updatable = false)
	private NexHomeFunction function;

	@Column(name = "read", nullable = false)
	private boolean read = false;

	@Column(name = "write", nullable = false)
	private boolean write = false;

	@Column(name = "delete", nullable = false)
	private boolean delete = false;

	@Column(name = "active_flag", nullable = false)
	private boolean activeFlag = true;

	@CreationTimestamp
	@Column(name = "creation_date", nullable = false, updatable = false)
	private Instant creationDate;

	@Column(name = "created_by", updatable = false)
	private Long createdBy;

	@UpdateTimestamp
	@Column(name = "last_update_date", nullable = false)
	private Instant lastUpdateDate;

	@Column(name = "last_update_by")
	private Long lastUpdateBy;

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
