package com.nexhome.app.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.nexhome.app.config.NexHomePushProperties;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends pushes through Firebase Cloud Messaging's HTTP v1 API ({@code nexhome.push.provider=fcm}). It signs in as the
 * service account of {@code FIREBASE_CREDENTIALS_FILE} (an RS256-signed JWT exchanged at Google's token endpoint for
 * an access token with the {@code firebase.messaging} scope, kept until 5 minutes before it expires) and posts
 * {@code {"message": {"token", "notification": {title, body}, "data", "android": {priority, notification:
 * {channel_id}}}}} to {@code projects/<id>/messages:send}. HIGH pushes use the apps' {@code alerts} channel, the rest
 * {@code default}. No extra library: the JDK signs the JWT. Neither the key nor the access token is logged.
 */
@Component
@ConditionalOnProperty(name = "nexhome.push.provider", havingValue = "fcm")
@Slf4j
public class NexHomeFcmPushSender implements NexHomePushSender {

	private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
	private static final String SEND_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";

	private final String clientEmail;
	private final PrivateKey privateKey;
	private final String tokenUri;
	private final String projectId;
	private final RestClient restClient;
	private final JsonMapper jsonMapper;

	private String accessToken;
	private Instant accessTokenExpiresAt = Instant.EPOCH;

	@Autowired
	public NexHomeFcmPushSender(NexHomePushProperties properties, JsonMapper jsonMapper) {
		this(properties, jsonMapper, readKey(properties.fcm().credentialsFile(), jsonMapper),
				RestClient.builder().requestFactory(timeouts(properties.fcm().timeoutSeconds())).build());
	}

	/** With a parsed key and a ready client (tests bind it to a mock server). */
	NexHomeFcmPushSender(NexHomePushProperties properties, JsonMapper jsonMapper, JsonNode key, RestClient restClient) {
		this.jsonMapper = jsonMapper;
		this.restClient = restClient;
		this.clientEmail = text(key, "client_email");
		this.tokenUri = key.has("token_uri") ? text(key, "token_uri") : "https://oauth2.googleapis.com/token";
		String configured = properties.fcm().projectId();
		this.projectId = configured == null || configured.isBlank() ? text(key, "project_id") : configured.trim();
		this.privateKey = privateKey(text(key, "private_key"));
		log.info("Push notifications through Firebase Cloud Messaging, project {}", projectId);
	}

	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	public String send(String deviceToken, String title, String body, Map<String, String> data, boolean high) {
		Map<String, Object> android = new LinkedHashMap<>();
		android.put("priority", high ? "HIGH" : "NORMAL");
		android.put("notification", Map.of("channel_id", high ? "alerts" : "default", "sound", "default"));
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("token", deviceToken);
		message.put("notification", Map.of("title", title, "body", body));
		message.put("data", data);
		message.put("android", android);
		try {
			JsonNode answer = restClient.post()
					.uri(SEND_URL.formatted(projectId))
					.header("Authorization", "Bearer " + accessToken())
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("message", message))
					.retrieve()
					.body(JsonNode.class);
			return answer == null || !answer.has("name") ? null : answer.get("name").asString();
		}
		catch (RestClientResponseException ex) {
			String error = ex.getResponseBodyAsString();
			// UNREGISTERED (404) or an invalid token (400 INVALID_ARGUMENT about the registration token)
			boolean dead = ex.getStatusCode().value() == 404 || error.contains("UNREGISTERED")
					|| (ex.getStatusCode().value() == 400 && error.contains("registration token"));
			throw new NexHomePushException("FCM refused the push (" + ex.getStatusCode().value() + "): "
					+ shorten(error), dead, ex);
		}
		catch (RestClientException ex) {
			throw new NexHomePushException("FCM could not be reached: " + ex.getMessage(), false, ex);
		}
	}

	/** A valid OAuth access token, renewed 5 minutes before it expires. */
	private synchronized String accessToken() {
		if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt.minusSeconds(300))) {
			return accessToken;
		}
		long now = Instant.now().getEpochSecond();
		String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", clientEmail);
		claims.put("scope", SCOPE);
		claims.put("aud", tokenUri);
		claims.put("iat", now);
		claims.put("exp", now + 3600);
		String unsigned = header + "." + base64Url(jsonMapper.writeValueAsBytes(claims));
		String assertion;
		try {
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(privateKey);
			signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
			assertion = unsigned + "." + base64Url(signature.sign());
		}
		catch (GeneralSecurityException ex) {
			throw new NexHomePushException("The Firebase service account key could not sign in.", false, ex);
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
		form.add("assertion", assertion);
		JsonNode answer;
		try {
			answer = restClient.post()
					.uri(tokenUri)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(JsonNode.class);
		}
		catch (RestClientException ex) {
			throw new NexHomePushException("Google refused the Firebase service account: " + ex.getMessage(), false, ex);
		}
		if (answer == null || !answer.has("access_token")) {
			throw new NexHomePushException("Google gave no access token for the Firebase service account.", false, null);
		}
		accessToken = answer.get("access_token").asString();
		long expiresIn = answer.has("expires_in") ? answer.get("expires_in").asLong() : 3600;
		accessTokenExpiresAt = Instant.now().plusSeconds(expiresIn);
		return accessToken;
	}

	private static JsonNode readKey(String file, JsonMapper jsonMapper) {
		try {
			return jsonMapper.readTree(Files.readString(Path.of(file.trim())));
		}
		catch (IOException | RuntimeException ex) {
			throw new IllegalStateException("The Firebase service account key (FIREBASE_CREDENTIALS_FILE) could not be "
					+ "read: " + ex.getMessage(), ex);
		}
	}

	private static PrivateKey privateKey(String pem) {
		String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		try {
			return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder()
					.decode(base64)));
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			throw new IllegalStateException("The Firebase service account key has no valid private_key.", ex);
		}
	}

	private static String text(JsonNode key, String field) {
		JsonNode value = key.get(field);
		if (value == null || value.asString().isBlank()) {
			throw new IllegalStateException("The Firebase service account key has no " + field + ".");
		}
		return value.asString();
	}

	private static String base64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String shorten(String text) {
		return text == null ? "" : text.length() > 300 ? text.substring(0, 300) + "..." : text;
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
