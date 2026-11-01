package com.nexhome.app.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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
 * An uploaded file (V47, not in the PDF): its bytes are in object storage under {@code storageKey}. UPLOADED, then
 * ATTACHED to what uses it ({@code referenceType} / {@code referenceId}), DELETED when replaced or removed (the row
 * stays; the stored object is removed). Purposes are {@link NexHomeLookupCodes.FilePurpose} values.
 */
@Entity
@Table(name = "nexhome_files")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeFile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Generated
	@Column(name = "public_id", nullable = false, insertable = false, updatable = false, length = 24)
	private String publicId;

	/** The unguessable id in public addresses and signed links. */
	@Generated
	@Column(name = "file_uid", nullable = false, insertable = false, updatable = false)
	private UUID fileUid;

	@Column(name = "purpose", nullable = false, updatable = false, length = 40)
	private String purpose;

	/** PUBLIC or PRIVATE. */
	@Column(name = "visibility", nullable = false, updatable = false, length = 20)
	private String visibility;

	/** LOCAL or S3. */
	@Column(name = "storage_provider", nullable = false, updatable = false, length = 20)
	private String storageProvider;

	@Column(name = "bucket_name", updatable = false, length = 100)
	private String bucketName;

	@Column(name = "storage_key", nullable = false, updatable = false, length = 500)
	private String storageKey;

	@Column(name = "original_name", nullable = false, updatable = false, length = 255)
	private String originalName;

	/** From the file's own bytes, never the client's word. */
	@Column(name = "content_type", nullable = false, updatable = false, length = 100)
	private String contentType;

	@Column(name = "file_size", nullable = false, updatable = false)
	private long fileSize;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "checksum_sha256", nullable = false, updatable = false, length = 64)
	private String checksumSha256;

	@Column(name = "file_status", nullable = false, length = 20)
	private String fileStatus;

	@Column(name = "reference_type", length = 40)
	private String referenceType;

	@Column(name = "reference_id")
	private Long referenceId;

	@Column(name = "attached_at")
	private Instant attachedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "deleted_by")
	private Long deletedBy;

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
