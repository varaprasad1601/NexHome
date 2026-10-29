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
 * A booking offered to a partner, and the answer. At most one ACCEPTED (or COMPLETED) per booking (partial unique
 * index {@code ux_nexhome_booking_assignments_one_accepted}).
 */
@Entity
@Table(name = "nexhome_booking_assignments")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeBookingAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeBooking booking;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "partner_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomePartnerProfile partner;

	@Column(name = "assigned_by", updatable = false)
	private Long assignedBy;

	/** ASSIGNMENT_STATUS lookup code. */
	@Column(name = "assignment_status", nullable = false, length = 60)
	private String assignmentStatus;

	@Column(name = "offered_at", nullable = false, updatable = false)
	private Instant offeredAt;

	@Column(name = "responded_at")
	private Instant respondedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "rejection_reason", columnDefinition = "text")
	private String rejectionReason;

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
