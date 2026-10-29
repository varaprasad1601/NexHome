package com.nexhome.app.mapper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Formatted text from the console's editor (service descriptions): HTML limited to paragraphs, line breaks, bold,
 * italic, underline and lists, without any attributes. Everything else (scripts, links, styles, event handlers) is
 * removed, keeping its text, so the HTML is safe to show customers.
 */
public final class NexHomeRichText {

	private static final Safelist ALLOWED = new Safelist()
			.addTags("p", "br", "b", "strong", "i", "em", "u", "ul", "ol", "li");

	private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

	private NexHomeRichText() {
	}

	/** The cleaned HTML, or null when it has no text. Plain text stays as it is (escaped). */
	public static String clean(String html) {
		if (html == null) {
			return null;
		}
		String cleaned = Jsoup.clean(html, "", ALLOWED, OUTPUT).trim();
		return Jsoup.parse(cleaned).text().isBlank() ? null : cleaned;
	}
}
