package com.nexhome.app.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NexHomeRichTextTest {

	@Test
	void keepsTheEditorsFormatting() {
		String html = "<p>Includes <b>dusting</b>, <i>mopping</i> and <u>windows</u>.</p>"
				+ "<ul><li>Kitchen</li><li>Bathrooms</li></ul><ol><li>One</li></ol><p>Line<br>break</p>";

		assertThat(NexHomeRichText.clean(html)).isEqualTo(html);
	}

	@Test
	void removesScriptsLinksAttributesAndOtherTagsButKeepsTheirText() {
		assertThat(NexHomeRichText.clean("<p onclick=\"steal()\" style=\"color:red\">Hi <a href=\"javascript:x\">there</a>"
				+ "<script>alert(1)</script><img src=x onerror=alert(1)></p>"))
				.isEqualTo("<p>Hi there</p>");
		assertThat(NexHomeRichText.clean("<div><h1>Title</h1><span>text</span></div>")).isEqualTo("Titletext");
	}

	@Test
	void plainTextIsEscapedAndEmptyTextIsNull() {
		assertThat(NexHomeRichText.clean("5 < 6 & more")).isEqualTo("5 &lt; 6 &amp; more");
		assertThat(NexHomeRichText.clean("<p><br></p>")).isNull();
		assertThat(NexHomeRichText.clean("  ")).isNull();
		assertThat(NexHomeRichText.clean(null)).isNull();
	}
}
