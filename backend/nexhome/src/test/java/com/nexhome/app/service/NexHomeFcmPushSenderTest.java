package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nexhome.app.config.NexHomePushProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Firebase Cloud Messaging's HTTP v1 requests against a mock server (no push is sent): the service account sign-in
 * (a JWT signed with a throwaway key), the message, a dead token, and the properties' checks.
 */
class NexHomeFcmPushSenderTest {

	private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
	private static final String SEND_URL = "https://fcm.googleapis.com/v1/projects/nexhome-test/messages:send";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private MockRestServiceServer server;
	private NexHomeFcmPushSender sender;

	@BeforeEach
	void setUp() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
		JsonNode key = jsonMapper.valueToTree(Map.of("type", "service_account", "project_id", "nexhome-test",
				"client_email", "push@nexhome-test.iam.gserviceaccount.com", "private_key", pem, "token_uri", TOKEN_URI));
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		NexHomePushProperties properties = new NexHomePushProperties("fcm",
				new NexHomePushProperties.Fcm("unused.json", "", 5));
		sender = new NexHomeFcmPushSender(properties, jsonMapper, key, builder.build());
	}

	@Test
	void signsInOnceAndSendsTheMessage() {
		server.expect(ExpectedCount.once(), requestTo(TOKEN_URI))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("{\"access_token\": \"ya29.test\", \"expires_in\": 3600}",
						MediaType.APPLICATION_JSON));
		server.expect(ExpectedCount.twice(), requestTo(SEND_URL))
				.andExpect(header("Authorization", "Bearer ya29.test"))
				.andExpect(content().json("""
						{"message": {"token": "device-1", "notification": {"title": "Booking confirmed",
						 "body": "See you soon"}, "data": {"referenceType": "BOOKING", "referenceId": "7"},
						 "android": {"priority": "HIGH", "notification": {"channel_id": "alerts"}}}}"""))
				.andRespond(withSuccess("{\"name\": \"projects/nexhome-test/messages/0:123\"}",
						MediaType.APPLICATION_JSON));

		Map<String, String> data = Map.of("referenceType", "BOOKING", "referenceId", "7");
		assertThat(sender.send("device-1", "Booking confirmed", "See you soon", data, true))
				.isEqualTo("projects/nexhome-test/messages/0:123");
		assertThat(sender.send("device-1", "Booking confirmed", "See you soon", data, true)).isNotNull();
		server.verify();
	}

	@Test
	void anUnregisteredTokenIsDead() {
		server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("{\"access_token\": \"ya29.test\"}",
				MediaType.APPLICATION_JSON));
		server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\": {\"status\": \"NOT_FOUND\", \"details\": [{\"errorCode\": \"UNREGISTERED\"}]}}"));

		assertThatThrownBy(() -> sender.send("old-device", "T", "B", Map.of(), false))
				.isInstanceOfSatisfying(NexHomePushSender.NexHomePushException.class,
						ex -> assertThat(ex.unregistered()).isTrue());
	}

	@Test
	void fcmNeedsTheServiceAccountKey() {
		assertThatThrownBy(() -> new NexHomePushProperties("fcm", new NexHomePushProperties.Fcm("", "", 5)))
				.isInstanceOf(IllegalStateException.class);
		assertThat(new NexHomePushProperties(null, null).provider()).isEqualTo("log");
	}
}
