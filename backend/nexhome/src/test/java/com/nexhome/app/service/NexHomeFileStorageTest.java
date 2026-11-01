package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nexhome.app.config.NexHomeStorageProperties;
import com.nexhome.app.exception.ResourceNotFoundException;

/**
 * The storages without AWS: the local folder (write, read, delete, nothing outside its root), S3's presigned links
 * (signed offline: bucket, region, expiry and the file's name), the settings' checks, and type detection by bytes.
 */
class NexHomeFileStorageTest {

	@TempDir
	Path root;

	@Test
	void localStorageKeepsFilesInsideItsFolder() throws Exception {
		NexHomeLocalFileStorage storage = new NexHomeLocalFileStorage(new NexHomeStorageProperties("local",
				new NexHomeStorageProperties.Local(root.toString()), null, null, 0, 0));
		storage.start();

		storage.put("partner-documents/2026/11/a.pdf", "%PDF-1".getBytes(StandardCharsets.US_ASCII), "application/pdf");
		assertThat(Files.exists(root.resolve("partner-documents/2026/11/a.pdf"))).isTrue();
		assertThat(storage.get("partner-documents/2026/11/a.pdf")).asString().isEqualTo("%PDF-1");
		assertThat(storage.signedUrl("partner-documents/2026/11/a.pdf", Duration.ofMinutes(5), "a.pdf",
				"application/pdf")).isNull();

		storage.delete("partner-documents/2026/11/a.pdf");
		storage.delete("partner-documents/2026/11/a.pdf");
		assertThatThrownBy(() -> storage.get("partner-documents/2026/11/a.pdf"))
				.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> storage.put("../outside.pdf", new byte[1], "application/pdf"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void s3SignsShortLivedLinksOffline() {
		NexHomeS3FileStorage storage = new NexHomeS3FileStorage(new NexHomeStorageProperties("s3", null,
				new NexHomeStorageProperties.S3("nexhome-files-test", "ap-south-1", "AKIAEXAMPLEKEY000000",
						"secretsecretsecretsecretsecretsecret0000", null), null, 5, 24));
		try {
			String url = storage.signedUrl("partner-documents/2026/11/b.pdf", Duration.ofMinutes(5), "Aadhaar.pdf",
					"application/pdf");
			assertThat(url).startsWith("https://nexhome-files-test.s3.ap-south-1.amazonaws.com/partner-documents/")
					.contains("X-Amz-Expires=300")
					.contains("response-content-type=application%2Fpdf")
					.contains("Aadhaar.pdf")
					.doesNotContain("secretsecret");
			assertThat(storage.providerName()).isEqualTo("S3");
			assertThat(storage.bucketName()).isEqualTo("nexhome-files-test");
		}
		finally {
			storage.stop();
		}
	}

	@Test
	void r2SignsPathStyleLinksOnItsAccountEndpoint() {
		NexHomeR2FileStorage storage = new NexHomeR2FileStorage(new NexHomeStorageProperties("r2", null, null,
				new NexHomeStorageProperties.R2("https://0123abcd.r2.cloudflarestorage.com/nexhome-files-test/",
						"nexhome-files-test", "r2accesskey000", "r2secretsecretsecretsecret0000"), 5, 24));
		try {
			String url = storage.signedUrl("partner-documents/2026/11/c.pdf", Duration.ofMinutes(5), "PAN.pdf",
					"application/pdf");
			assertThat(url)
					.startsWith("https://0123abcd.r2.cloudflarestorage.com/nexhome-files-test/partner-documents/2026/11/")
					.contains("X-Amz-Expires=300")
					.contains("%2Fauto%2Fs3%2Faws4_request")
					.doesNotContain("r2secret");
			assertThat(storage.providerName()).isEqualTo("R2");
			assertThat(storage.bucketName()).isEqualTo("nexhome-files-test");
		}
		finally {
			storage.stop();
		}
		assertThatThrownBy(() -> new NexHomeStorageProperties("r2", null, null,
				new NexHomeStorageProperties.R2("https://x.r2.cloudflarestorage.com", "b", " ", "s"), 0, 0))
				.hasMessage("nexhome.storage.provider is r2, so R2_ENDPOINT, R2_BUCKET, R2_ACCESS_KEY and "
						+ "R2_SECRET_KEY must be set.");
		assertThat(new NexHomeStorageProperties.R2("e", "b", "key", "secret").toString())
				.doesNotContain("secret").doesNotContain("key");
	}

	@Test
	void settingsAreChecked() {
		assertThatThrownBy(() -> new NexHomeStorageProperties("s3", null, new NexHomeStorageProperties.S3(" ",
				"ap-south-1", null, null, null), null, 0, 0))
				.hasMessage("nexhome.storage.provider is s3, so S3_BUCKET and S3_REGION must be set.");
		assertThatThrownBy(() -> new NexHomeStorageProperties("ftp", null, null, null, 0, 0))
				.hasMessage("nexhome.storage.provider must be local, s3 or r2, not ftp.");
		NexHomeStorageProperties defaults = new NexHomeStorageProperties(null,
				new NexHomeStorageProperties.Local("x"), null, null, 0, 0);
		assertThat(defaults.provider()).isEqualTo("local");
		assertThat(defaults.downloadUrlMinutes()).isEqualTo(5);
		assertThat(defaults.unattachedHours()).isEqualTo(24);
		assertThat(new NexHomeStorageProperties.S3("b", "r", "key", "secret", null).toString())
				.doesNotContain("secret").doesNotContain("key");
	}

	@Test
	void typesComeFromTheBytes() {
		assertThat(NexHomeFiles.detectType("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isEqualTo("application/pdf");
		assertThat(NexHomeFiles.detectType(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF })).isEqualTo("image/jpeg");
		assertThat(NexHomeFiles.detectType("GIF89a".getBytes(StandardCharsets.US_ASCII))).isNull();
		assertThat(NexHomeFiles.detectType(new byte[0])).isNull();
		assertThat(NexHomeFiles.fileName("C:\\Users\\me\\scan.pdf", "file")).isEqualTo("scan.pdf");
		assertThat(NexHomeFiles.fileName("  ", "file")).isEqualTo("file");
	}
}
