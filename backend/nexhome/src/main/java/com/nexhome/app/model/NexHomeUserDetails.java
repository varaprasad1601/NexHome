package com.nexhome.app.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "nexhome_user_details")
@Getter
@Setter
@NoArgsConstructor
public class NexHomeUserDetails {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_details_id")
	private Long userDetailsId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	// The owning user is fixed once the details are created
	@JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false, unique = true, updatable = false)
	private NexHomeUser user;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", length = 100)
	private String lastName;

	@Column(name = "full_name", length = 201)
	private String fullName;

    /**
     * All addresses of this user (at most one active), loaded lazily. Read-only: the join column is not insertable
     * or updatable and nothing cascades, so creating or updating user details never inserts, changes or unlinks
     * addresses. Addresses are saved through NexHomeUserAddressRepository.
     */
    @OneToMany(fetch = FetchType.LAZY)
    // NO_CONSTRAINT: addresses reference nexhome_users only; no foreign key to nexhome_user_details
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private List<NexHomeUserAddress> addresses = new ArrayList<>();

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
