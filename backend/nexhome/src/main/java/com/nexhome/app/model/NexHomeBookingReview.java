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
 * A customer's rating of a completed booking (Module 9): the service and the partner, 1 to 5 each, and an optional
 * text. One per booking ({@code uq_nexhome_booking_reviews_booking}). The partner's rating counts while PUBLISHED.
 */
@Entity
@Table(name = "nexhome_booking_reviews")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeBookingReview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeBooking booking;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeCustomerProfile customer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "partner_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomePartnerProfile partner;

	@Column(name = "service_rating", nullable = false)
	private Short serviceRating;

	@Column(name = "partner_rating", nullable = false)
	private Short partnerRating;

	@Column(name = "review_text", columnDefinition = "text")
	private String reviewText;

	/** REVIEW_STATUS value code: PUBLISHED, HIDDEN or UNDER_REVIEW. */
	@Column(name = "review_status", nullable = false, length = 60)
	private String reviewStatus;

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
