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
 * A file attached to a support ticket or one of its messages. Only its storage key is kept: there is no object
 * storage yet, so the client supplies the key (as for partner documents).
 */
@Entity
@Table(name = "nexhome_support_ticket_attachments")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeSupportTicketAttachment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ticket_id", referencedColumnName = "id", nullable = false, updatable = false)
	private NexHomeSupportTicket ticket;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "message_id", referencedColumnName = "id", updatable = false)
	private NexHomeSupportTicketMessage supportMessage;

	@Column(name = "storage_key", nullable = false, columnDefinition = "text", updatable = false)
	private String storageKey;

	@Column(name = "file_type", nullable = false, length = 100, updatable = false)
	private String fileType;

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
