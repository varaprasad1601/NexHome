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

	/**
	 * A profile reached through a join (e.g. a booking's customer) whose first name, last name, "first last" or
	 * public id contains the pattern (from containsPattern), case-insensitively; a missing profile matches nothing.
	 */
	static jakarta.persistence.criteria.Predicate personMatches(jakarta.persistence.criteria.CriteriaBuilder cb,
			jakarta.persistence.criteria.Path<?> person, String pattern) {
		jakarta.persistence.criteria.Expression<String> first = cb.lower(cb.coalesce(person.<String>get("firstName"), ""));
		jakarta.persistence.criteria.Expression<String> last = cb.lower(cb.coalesce(person.<String>get("lastName"), ""));
		return cb.or(cb.like(first, pattern, '\\'), cb.like(last, pattern, '\\'),
				cb.like(cb.concat(cb.concat(first, " "), last), pattern, '\\'),
				cb.like(cb.lower(person.<String>get("publicId")), pattern, '\\'));
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
