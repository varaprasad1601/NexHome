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
 * An in-app notification for one user (Module 9). {@code referenceType} / {@code referenceId} say what it is about
 * (e.g. BOOKING and the booking's id), checked by the service that sends it; {@code readAt} is set once read.
 */
@Entity
@Table(name = "nexhome_notifications")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeUser user;

	/** NOTIFICATION_TYPE value code. */
	@Column(name = "notification_type", nullable = false, length = 50, updatable = false)
	private String notificationType;

	@Column(name = "title", nullable = false, length = 200, updatable = false)
	private String title;

	@Column(name = "message", nullable = false, columnDefinition = "text", updatable = false)
	private String message;

	@Column(name = "reference_type", length = 40, updatable = false)
	private String referenceType;

	@Column(name = "reference_id", updatable = false)
	private Long referenceId;

	@Column(name = "read_at")
	private Instant readAt;

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
