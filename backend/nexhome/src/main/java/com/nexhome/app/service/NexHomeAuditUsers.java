package com.nexhome.app.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeAuditEntry;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.repository.NexHomeAdminProfileRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomePartnerProfileRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Turns audit lines (event, subject, when, user id) into {@link NexHomeAuditEntry} with each user's name and role,
 * loading every user's profiles and roles in one query each.
 */
@Component
@RequiredArgsConstructor
public class NexHomeAuditUsers {

	/** An update this soon after the creation is part of it (a create that saves twice). */
	public static final Duration SAME_CHANGE = Duration.ofSeconds(5);

	private static final Set<String> STAFF_ROLES = Set.of(NexHomeRole.SUPER_USER, NexHomeRole.ADMIN,
			NexHomeRole.SUB_ADMIN);

	private final NexHomeUserRepository userRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeAdminProfileRepository adminProfileRepository;
	private final NexHomePartnerProfileRepository partnerProfileRepository;
	private final NexHomeCustomerProfileRepository customerProfileRepository;

	/** One audit line before the user's name and role are known. */
	public record Line(String event, String subject, Instant at, Long by) {
	}

	/** Whether {@code updatedDate} is a separate change after {@code creationDate}. */
	public static boolean updatedLater(Instant creationDate, Instant updatedDate) {
		return creationDate != null && updatedDate != null && updatedDate.isAfter(creationDate.plus(SAME_CHANGE));
	}

	/** The lines in the given order, with names and roles. */
	public List<NexHomeAuditEntry> entries(List<Line> lines) {
		Set<Long> userIds = lines.stream().map(Line::by).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, NexHomeRole> roles = highestRoles(userIds);
		Map<Long, String> names = names(userIds, roles);
		return lines.stream()
				.map(line -> {
					NexHomeRole role = line.by() == null ? null : roles.get(line.by());
					return new NexHomeAuditEntry(line.event(), line.subject(), line.at(), line.by(),
							line.by() == null ? null : names.get(line.by()), role == null ? null : role.getName());
				})
				.toList();
	}

	/** Only what has happened, newest first. */
	public List<NexHomeAuditEntry> newestFirst(List<Line> lines) {
		return entries(lines.stream()
				.filter(line -> line.at() != null)
				.sorted((a, b) -> b.at().compareTo(a.at()))
				.toList());
	}

	/**
	 * Display names. Staff (highest role SUPER_USER, ADMIN or SUB_ADMIN) go by their staff profile's name only, never
	 * by a customer or partner profile the same account may have; others by their partner, else customer profile's
	 * name. Without a name (no profile, or one not filled in yet) the mobile number is shown; user 1 is "System".
	 */
	private Map<Long, String> names(Set<Long> userIds, Map<Long, NexHomeRole> roles) {
		Map<Long, String> names = new HashMap<>();
		if (userIds.isEmpty()) {
			return names;
		}
		Set<Long> staff = userIds.stream()
				.filter(userId -> roles.get(userId) != null && STAFF_ROLES.contains(roles.get(userId).getCode()))
				.collect(Collectors.toSet());
		Set<Long> others = userIds.stream().filter(userId -> !staff.contains(userId)).collect(Collectors.toSet());
		if (!staff.isEmpty()) {
			adminProfileRepository.findByUserIdIn(staff).stream()
					.filter(profile -> staff.contains(profile.getUser().getId()))
					.forEach(profile -> putName(names, profile.getUser().getId(), profile.getFirstName(),
							profile.getLastName()));
		}
		if (!others.isEmpty()) {
			partnerProfileRepository.findByUserIdIn(others).stream()
					.filter(profile -> others.contains(profile.getUser().getId()))
					.forEach(profile -> putName(names, profile.getUser().getId(), profile.getFirstName(),
							profile.getLastName()));
			customerProfileRepository.findByUserIdIn(others).stream()
					.filter(profile -> others.contains(profile.getUser().getId()))
					.forEach(profile -> putName(names, profile.getUser().getId(), profile.getFirstName(),
							profile.getLastName()));
		}
		userRepository.findAllById(userIds).forEach(user -> names.putIfAbsent(user.getId(),
				user.getId() == 1L ? "System" : user.getCountryCode() + " " + user.getMobileNumber()));
		return names;
	}

	/**
	 * Each user's highest active role: role ids follow rank (SUPER_USER 1, ADMIN 2, SUB_ADMIN 3, ...) and the query
	 * returns them in id order.
	 */
	private Map<Long, NexHomeRole> highestRoles(Set<Long> userIds) {
		Map<Long, NexHomeRole> roles = new HashMap<>();
		if (userIds.isEmpty()) {
			return roles;
		}
		userRoleRepository.findWithRoleByUserIdIn(userIds).stream()
				.filter(userRole -> userRole.isActive() && userRole.getRole().isActiveFlag())
				.forEach(userRole -> roles.putIfAbsent(userRole.getUser().getId(), userRole.getRole()));
		return roles;
	}

	/** Puts the name when the profile has one (names are optional until the profile is filled in). */
	private static void putName(Map<Long, String> names, Long userId, String firstName, String lastName) {
		if (userId == null) {
			return;
		}
		String name = NexHomeText.trimToNull(String.join(" ", firstName == null ? "" : firstName,
				lastName == null ? "" : lastName));
		if (name != null) {
			names.putIfAbsent(userId, name);
		}
	}
}
