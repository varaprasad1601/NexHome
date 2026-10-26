package com.nexhome.app.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Encrypts sensitive values for the {@code *_encrypted} columns (Aadhaar, document and bank account numbers) with
 * AES-256-GCM. Each value gets a random IV, so equal numbers encrypt differently; the stored text is
 * {@code v1:} followed by Base64 of IV + ciphertext. Plain values must never be logged.
 */
@Component
public class NexHomeFieldEncryptor {

	private static final String PREFIX = "v1:";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	public NexHomeFieldEncryptor(NexHomeSecurityProperties properties) {
		byte[] bytes;
		try {
			String encoded = properties.dataEncryptionKey();
			bytes = Base64.getDecoder().decode(encoded == null ? "" : encoded.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(
					"nexhome.security.data-encryption-key (DATA_ENCRYPTION_KEY) must be Base64-encoded", ex);
		}
		if (bytes.length != 32) {
			throw new IllegalStateException("nexhome.security.data-encryption-key (DATA_ENCRYPTION_KEY) must be a "
					+ "Base64-encoded key of exactly 32 bytes");
		}
		this.key = new SecretKeySpec(bytes, "AES");
	}

	/** Null stays null. */
	public String encrypt(String plain) {
		if (plain == null) {
			return null;
		}
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] stored = ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
			return PREFIX + Base64.getEncoder().encodeToString(stored);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Could not encrypt a value", ex);
		}
	}

	/** Null stays null. */
	public String decrypt(String stored) {
		if (stored == null) {
			return null;
		}
		if (!stored.startsWith(PREFIX)) {
			throw new IllegalStateException("Unknown encrypted value format");
		}
		try {
			byte[] bytes = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
			return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			// Wrong key or tampered value; the message deliberately carries no data
			throw new IllegalStateException("Could not decrypt a value", ex);
		}
	}

	/** Shows only the last four characters, e.g. {@code XXXXXXXX9012}. Null stays null. */
	public static String mask(String plain) {
		if (plain == null) {
			return null;
		}
		int visible = Math.min(4, plain.length());
		return "X".repeat(plain.length() - visible) + plain.substring(plain.length() - visible);
	}
}
