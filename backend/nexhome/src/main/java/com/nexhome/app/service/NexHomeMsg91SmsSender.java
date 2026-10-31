package com.nexhome.app.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.nexhome.app.config.NexHomeSmsProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends OTPs through MSG91's Flow API ({@code nexhome.sms.provider=msg91}): a POST to the flow URL with the
 * {@code authkey} header and {@code {"template_id", "short_url": "0", "recipients": [{"mobiles": "91...", "otp":
 * "123456"}]}}, the OTP under the template's variable. MSG91 answers {@code {"type": "success", "message": "<request
 * id>"}}; anything else, or no answer within the timeout, is a {@link NexHomeSmsException}. Neither the OTP nor the
 * auth key is ever logged.
 */
@Component
@ConditionalOnProperty(name = "nexhome.sms.provider", havingValue = "msg91")
@Slf4j
public class NexHomeMsg91SmsSender implements NexHomeSmsSender {

	private final NexHomeSmsProperties.Msg91 settings;
	private final RestClient restClient;

	/** Its own client with the timeouts (Spring Boot 4 has no shared RestClient.Builder without its restclient module). */
	@Autowired
	public NexHomeMsg91SmsSender(NexHomeSmsProperties properties) {
		this(properties, RestClient.builder().requestFactory(timeouts(properties.msg91().timeoutSeconds())).build());
	}

	/** With a ready client (tests bind it to a mock server). */
	NexHomeMsg91SmsSender(NexHomeSmsProperties properties, RestClient restClient) {
		this.settings = properties.msg91();
		this.restClient = restClient;
	}

	@Override
	public String providerName() {
		return "MSG91";
	}

	@Override
	public String sendOtp(String countryCode, String mobileNumber, String otp) {
		String variable = settings.otpVariable() == null || settings.otpVariable().isBlank() ? "otp"
				: settings.otpVariable().trim();
		Map<String, Object> body = Map.of(
				"template_id", settings.otpTemplateId(),
				"short_url", "0",
				"recipients", List.of(Map.of(
						"mobiles", NexHomeSmsSender.internationalNumber(countryCode, mobileNumber),
						variable, otp)));
		Map<?, ?> answer;
		try {
			answer = restClient.post()
					.uri(settings.flowUrl())
					.header("authkey", settings.authKey())
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map.class);
		}
		catch (RestClientException ex) {
			throw new NexHomeSmsException("MSG91 could not be reached or refused the request: " + ex.getMessage(), ex);
		}
		if (answer == null || !"success".equals(answer.get("type"))) {
			throw new NexHomeSmsException("MSG91 did not send the OTP: " + (answer == null ? "no answer"
					: answer.get("message")));
		}
		Object requestId = answer.get("message");
		log.info("OTP SMS sent through MSG91 (request {})", requestId);
		return requestId == null ? null : requestId.toString();
	}

	/** Connect and read timeouts; buffered, so the JSON goes with a Content-Length (not chunked). */
	private static BufferingClientHttpRequestFactory timeouts(int seconds) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		Duration timeout = Duration.ofSeconds(seconds > 0 ? seconds : 10);
		factory.setConnectTimeout(timeout);
		factory.setReadTimeout(timeout);
		return new BufferingClientHttpRequestFactory(factory);
	}
}
