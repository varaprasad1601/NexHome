package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nexhome.app.config.NexHomeSmsProperties;

/** The request MSG91's Flow API receives, and its answers, against a mock server (no SMS is sent). */
class NexHomeMsg91SmsSenderTest {

	private static final String FLOW_URL = "https://control.msg91.com/api/v5/flow";

	private MockRestServiceServer server;
	private NexHomeMsg91SmsSender sender;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		NexHomeSmsProperties properties = new NexHomeSmsProperties("msg91",
				new NexHomeSmsProperties.Msg91("test-auth-key", "tmpl-otp-1", "otp", FLOW_URL, 5));
		sender = new NexHomeMsg91SmsSender(properties, builder.build());
	}

	@Test
	void sendsTheOtpWithTheTemplateToTheInternationalNumber() {
		server.expect(requestTo(FLOW_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("authkey", "test-auth-key"))
				.andExpect(content().json("""
						{"template_id": "tmpl-otp-1", "short_url": "0",
						 "recipients": [{"mobiles": "919876543210", "otp": "482913"}]}""", true))
				.andRespond(withSuccess("{\"type\": \"success\", \"message\": \"3661686b6d6a\"}",
						MediaType.APPLICATION_JSON));

		assertThat(sender.sendOtp("+91", "9876543210", "482913")).isEqualTo("3661686b6d6a");
		assertThat(sender.providerName()).isEqualTo("MSG91");
		server.verify();
	}

	@Test
	void anErrorAnswerIsAFailure() {
		server.expect(requestTo(FLOW_URL))
				.andRespond(withSuccess("{\"type\": \"error\", \"message\": \"Invalid template\"}",
						MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> sender.sendOtp("+91", "9876543210", "482913"))
				.isInstanceOf(NexHomeSmsSender.NexHomeSmsException.class)
				.hasMessageContaining("Invalid template")
				.hasMessageNotContaining("482913");
	}

	@Test
	void aServerErrorIsAFailure() {
		server.expect(requestTo(FLOW_URL)).andRespond(withServerError());

		assertThatThrownBy(() -> sender.sendOtp("+91", "9876543210", "482913"))
				.isInstanceOf(NexHomeSmsSender.NexHomeSmsException.class);
	}

	@Test
	void msg91NeedsItsKeyAndTemplate() {
		assertThatThrownBy(() -> new NexHomeSmsProperties("msg91",
				new NexHomeSmsProperties.Msg91("", "tmpl", "otp", FLOW_URL, 5)))
				.isInstanceOf(IllegalStateException.class);
		assertThat(new NexHomeSmsProperties(null, null).provider()).isEqualTo("log");
	}
}
