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
 * One delivery attempt of a notification on one channel. In-app notifications are logged IN_APP / DELIVERED when
 * stored; push, SMS and email get their rows once their providers exist. The notification is kept as null when the
 * user deletes it.
 */
@Entity
@Table(name = "nexhome_notification_delivery_logs")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeNotificationDeliveryLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "notification_id", referencedColumnName = "id")
	private NexHomeNotification notification;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeUser user;

	/** NOTIFICATION_CHANNEL value code. */
	@Column(name = "channel", nullable = false, length = 60, updatable = false)
	private String channel;

	@Column(name = "provider_message_id", length = 150)
	private String providerMessageId;

	/** DELIVERY_STATUS value code. */
	@Column(name = "delivery_status", nullable = false, length = 60)
	private String deliveryStatus;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	@Column(name = "failure_reason", columnDefinition = "text")
	private String failureReason;

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
