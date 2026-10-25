package com.nexhome.app.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key (user_id, role_id) of {@link NexHomeUserRole}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class NexHomeUserRoleId implements Serializable {

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "role_id")
	private Short roleId;
}
