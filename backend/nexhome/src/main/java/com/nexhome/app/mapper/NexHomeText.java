package com.nexhome.app.mapper;

import java.text.Normalizer;
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

	/**
	 * A URL slug from a name: accents removed, lower-cased, and every run of other characters turned into one hyphen,
	 * e.g. "Deep Cleaning (2 BHK)" -> deep-cleaning-2-bhk. Empty when the name has no letters a-z or digits.
	 */
	public static String slugOf(String name) {
		String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
	}
}
