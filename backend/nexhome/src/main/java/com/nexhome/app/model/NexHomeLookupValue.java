package com.nexhome.app.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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
 * An allowed value of a lookup type. Business tables store the selected {@code valueCode} as plain text, with no
 * foreign key to this table.
 * <p>
 * At most one value per type is the default; the database enforces it with the partial unique index
 * {@code ux_nexhome_lookup_values_one_default}.
 */
@Entity
@Table(name = "nexhome_lookup_values",
		uniqueConstraints = @UniqueConstraint(columnNames = { "lookup_type_id", "value_code" }))
@Getter
@Setter
@NoArgsConstructor
public class NexHomeLookupValue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// Type and code identify the value and are fixed once it is created
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lookup_type_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeLookupType lookupType;

	@Column(name = "value_code", nullable = false, updatable = false, length = 80)
	private String valueCode;

	@Column(name = "display_name", nullable = false, length = 150)
	private String displayName;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "display_order", nullable = false)
	private int displayOrder = 0;

	@Column(name = "active_flag", nullable = false)
	private boolean activeFlag = true;

	@Column(name = "is_default", nullable = false)
	private boolean defaultValue = false;

	/** A JSON object as text, e.g. {@code {"color":"green"}}; never null. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
	private String metadata = "{}";

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
