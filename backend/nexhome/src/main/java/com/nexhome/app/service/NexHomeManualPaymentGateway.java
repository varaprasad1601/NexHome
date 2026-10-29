package com.nexhome.app.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The simulated provider MANUAL: every order, refund and transfer succeeds at once with a generated id, and payments
 * are captured when staff record them (or a webhook signed with {@code nexhome.payments.webhook-secret} reports
 * them). Webhook signatures are the hex HMAC-SHA256 of the request body.
 */
@Component
public class NexHomeManualPaymentGateway implements NexHomePaymentGateway {

	public static final String PROVIDER = "MANUAL";

	private final byte[] webhookSecret;

	public NexHomeManualPaymentGateway(
			@Value("${nexhome.payments.webhook-secret:}") String webhookSecret) {
		this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public String createOrder(String idempotencyKey, BigDecimal amount, String currency) {
		// Stable for the key, as a real gateway's idempotent order creation would be
		return "manual_order_" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8))
				.toString().replace("-", "").substring(0, 20);
	}

	@Override
	public String refund(String providerPaymentId, BigDecimal amount) {
		return "manual_refund_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
	}

	@Override
	public Map<String, Object> transfer(String idempotencyKey, BigDecimal amount, String accountReference) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("transferId", "manual_transfer_" + UUID.nameUUIDFromBytes(
				idempotencyKey.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "").substring(0, 20));
		response.put("status", "SETTLED");
		response.put("amount", amount);
		response.put("account", accountReference);
		response.put("simulated", true);
		return response;
	}

	@Override
	public boolean verifySignature(String payload, String signature) {
		// Without a key nothing is signed, so nothing is trusted
		if (webhookSecret.length == 0 || payload == null || signature == null) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(webhookSecret, "HmacSHA256"));
			byte[] expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)))
					.getBytes(StandardCharsets.UTF_8);
			// Constant time, so the comparison gives nothing away
			return MessageDigest.isEqual(expected, signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HMAC-SHA256 is not available", e);
		}
	}
}
