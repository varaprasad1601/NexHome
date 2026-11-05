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
 * A phone that receives push notifications (V53, not in the PDF): one install of the customer or partner app and its
 * Firebase Cloud Messaging registration token. The token is unique; a new sign-in on the same phone moves it to that
 * user. Inactive once the user logs out or FCM no longer knows the token ({@code deactivatedReason}).
 */
@Entity
@Table(name = "nexhome_push_devices")
@Getter
@Setter
@NoArgsConstructor
public class NexHomePushDevice {

	/** The apps that register devices ({@code app_code}). */
	public static final String CUSTOMER_APP = "CUSTOMER";
	public static final String PARTNER_APP = "PARTNER";
	/** Why a device stopped receiving pushes. */
	public static final String LOGGED_OUT = "LOGGED_OUT";
	public static final String UNREGISTERED = "UNREGISTERED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
	private NexHomeUser user;

	@Column(name = "device_token", nullable = false, length = 512, updatable = false)
	private String deviceToken;

	/** CUSTOMER or PARTNER. */
	@Column(name = "app_code", nullable = false, length = 20)
	private String appCode;

	/** ANDROID or IOS. */
	@Column(name = "platform", nullable = false, length = 20)
	private String platform;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@Column(name = "deactivated_reason", length = 40)
	private String deactivatedReason;

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
