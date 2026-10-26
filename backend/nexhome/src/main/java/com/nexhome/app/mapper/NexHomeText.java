package com.nexhome.app.mapper;

import java.util.Locale;

/**
 * Normalization of submitted text shared by the profile mappers and services.
 */
public final class NexHomeText {

	private NexHomeText() {
	}

	/** Trimmed, or null when null or blank. */
	public static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	/** Names: trimmed with inner whitespace runs collapsed to one space, or null when null or blank. */
	public static String collapseWhitespace(String value) {
		String trimmed = trimToNull(value);
		return trimmed == null ? null : trimmed.replaceAll("\\s+", " ");
	}

	/** Trimmed and upper-cased, or null when null or blank (codes such as IFSC, employee and country codes). */
	public static String upperCase(String value) {
		String trimmed = trimToNull(value);
		return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
	}
}
