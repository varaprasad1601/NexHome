package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The application starts with MSG91 chosen (dev DB): the sender is MSG91's and its dependencies exist. Nothing is
 * sent. (A missing RestClient.Builder once stopped startup only with this provider.)
 */
@SpringBootTest(properties = { "nexhome.sms.provider=msg91", "nexhome.sms.msg91.auth-key=test-key",
		"nexhome.sms.msg91.otp-template-id=test-template" })
@ActiveProfiles("dev")
class NexHomeSmsWiringTest {

	@Autowired
	private NexHomeSmsSender smsSender;

	@Test
	void msg91IsTheSender() {
		assertThat(smsSender).isInstanceOf(NexHomeMsg91SmsSender.class);
		assertThat(smsSender.providerName()).isEqualTo("MSG91");
	}
}
