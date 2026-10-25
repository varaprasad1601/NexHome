package com.nexhome.app.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Assignment of a role to a user (many-to-many). The user and role form the primary key, so they are fixed once the
 * row is created; an assignment is withdrawn by setting {@code isActive} to false.
 * <p>
 * The key is assigned, not generated, so Spring Data cannot tell a new row from its id; {@link Persistable} lets
 * {@code save()} insert a new assignment directly instead of trying to merge it.
 */
@Entity
@Table(name = "nexhome_user_roles")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeUserRole implements Persistable<NexHomeUserRoleId> {

	@EmbeddedId
	private NexHomeUserRoleId id = new NexHomeUserRoleId();

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeUser user;

	@MapsId("roleId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeRole role;

	@Column(name = "assigned_by")
	private Long assignedBy;

	@CreationTimestamp
	@Column(name = "assigned_at", nullable = false, updatable = false)
	private Instant assignedAt;

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

	@Transient
	@Getter(lombok.AccessLevel.NONE)
	@Setter(lombok.AccessLevel.NONE)
	private boolean newRow = true;

	@Override
	public boolean isNew() {
		return newRow;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		newRow = false;
	}
}
