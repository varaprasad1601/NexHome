package com.nexhome.app.repository;

import org.springframework.data.jpa.domain.Specification;

/**
 * Text search conditions shared by the profile repositories. Callers pass the search text trimmed and lower-cased.
 */
final class NexHomeSearch {

	private NexHomeSearch() {
	}

	/** A LIKE pattern matching values that contain the text, with %, _ and the escape character escaped. */
	static String containsPattern(String search) {
		return "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

	/** The first name, last name or public id contains the text, case-insensitively. */
	static <T> Specification<T> nameOrPublicIdContains(String search) {
		String pattern = containsPattern(search);
		return (profile, query, cb) -> cb.or(
				cb.like(cb.lower(profile.get("firstName")), pattern, '\\'),
				cb.like(cb.lower(profile.get("lastName")), pattern, '\\'),
				cb.like(cb.lower(profile.get("publicId")), pattern, '\\'));
	}
}
