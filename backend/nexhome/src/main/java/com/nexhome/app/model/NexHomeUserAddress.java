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
 * An address of a user. A user can have many addresses but at most one with active_flag = true; that rule is
 * enforced by the partial unique index ux_nexhome_user_addresses_one_active (user_id) WHERE active_flag.
 */
@Entity
@Table(name = "nexhome_user_addresses")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeUserAddress {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_address_id")
	private Long userAddressId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	// The owning user is fixed once the address is created
	@JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false, updatable = false)
	private NexHomeUser user;

	@Column(name = "address1", nullable = false)
	private String address1;

	@Column(name = "address2", nullable = false)
	private String address2;

	@Column(name = "address3")
	private String address3;

	@Column(name = "city", nullable = false, length = 100)
	private String city;

	@Column(name = "district", nullable = false, length = 100)
	private String district;

	@Column(name = "state", nullable = false, length = 100)
	private String state;

	@Column(name = "pin_code", nullable = false, length = 10)
	private String pinCode;

	@Column(name = "active_flag", nullable = false)
	private boolean activeFlag = true;

	@CreationTimestamp
	@Column(name = "creation_date", nullable = false, updatable = false)
	private Instant creationDate;

	@Column(name = "created_by", updatable = false)
	private Long createdBy;

	@UpdateTimestamp
	@Column(name = "last_update_date", nullable = false)
	private Instant lastUpdateDate;

	@Column(name = "last_update_by")
	private Long lastUpdateBy;

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
