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
 * An image of a service, stored in the database until file storage exists: JPEG, PNG or WebP, at most 5 MB, at most
 * 5 per service. Lists read it without the bytes ({@code NexHomeServiceImageRepository.Info}).
 */
@Entity
@Table(name = "nexhome_service_images")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeServiceImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	// The service is fixed once the image is added
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "service_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeService service;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	/** image/jpeg, image/png or image/webp, from the file's own bytes. */
	@Column(name = "content_type", nullable = false, length = 60)
	private String contentType;

	@Column(name = "file_size", nullable = false)
	private int fileSize;

	@Column(name = "image_data", nullable = false, updatable = false)
	private byte[] imageData;

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
