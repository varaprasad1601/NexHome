package com.nexhome.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

class NexHomeFieldEncryptorTest {

	private static NexHomeFieldEncryptor encryptor(byte[] key) {
		return new NexHomeFieldEncryptor(new NexHomeSecurityProperties("unused", "nexhome", 15, 30, List.of(),
				Base64.getEncoder().encodeToString(key)));
	}

	@Test
	void encryptsWithARandomIvAndDecryptsBack() {
		NexHomeFieldEncryptor encryptor = encryptor(new byte[32]);

		String first = encryptor.encrypt("234567890123");
		String second = encryptor.encrypt("234567890123");

		assertThat(first).startsWith("v1:").doesNotContain("234567890123").isNotEqualTo(second);
		assertThat(encryptor.decrypt(first)).isEqualTo("234567890123");
		assertThat(encryptor.decrypt(second)).isEqualTo("234567890123");
		assertThat(encryptor.encrypt(null)).isNull();
		assertThat(encryptor.decrypt(null)).isNull();
	}

	@Test
	void anotherKeyOrATamperedValueCannotBeDecrypted() {
		String stored = encryptor(new byte[32]).encrypt("123456784321");
		byte[] otherKey = new byte[32];
		otherKey[0] = 1;

		assertThatThrownBy(() -> encryptor(otherKey).decrypt(stored))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Could not decrypt a value");
		String tampered = stored.substring(0, stored.length() - 2) + (stored.endsWith("A=") ? "B=" : "A=");
		assertThatThrownBy(() -> encryptor(new byte[32]).decrypt(tampered))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void startupNeedsA32ByteKey() {
		assertThatThrownBy(() -> encryptor(new byte[16]))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("exactly 32 bytes");
		assertThatThrownBy(() -> new NexHomeFieldEncryptor(
				new NexHomeSecurityProperties("unused", "nexhome", 15, 30, List.of(), null)))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new NexHomeFieldEncryptor(
				new NexHomeSecurityProperties("unused", "nexhome", 15, 30, List.of(), "not base64!")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Base64");
	}

	@Test
	void masksAllButTheLastFourCharacters() {
		assertThat(NexHomeFieldEncryptor.mask("234567890123")).isEqualTo("XXXXXXXX0123");
		assertThat(NexHomeFieldEncryptor.mask("123")).isEqualTo("123");
		assertThat(NexHomeFieldEncryptor.mask(null)).isNull();
	}
}
