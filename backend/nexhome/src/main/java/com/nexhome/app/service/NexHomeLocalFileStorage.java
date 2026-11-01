package com.nexhome.app.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.nexhome.app.config.NexHomeStorageProperties;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.exception.ServiceUnavailableException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Files in a folder on this machine ({@code nexhome.storage.provider=local}, the default until the AWS account
 * exists; {@code nexhome.storage.local.root}). Private files are downloaded through links the application signs.
 */
@Component
@ConditionalOnProperty(name = "nexhome.storage.provider", havingValue = "local", matchIfMissing = true)
@Slf4j
public class NexHomeLocalFileStorage implements NexHomeFileStorage {

	static final String PROVIDER = "LOCAL";

	private final Path root;

	public NexHomeLocalFileStorage(NexHomeStorageProperties properties) {
		this.root = Path.of(properties.local().root()).toAbsolutePath().normalize();
	}

	@PostConstruct
	void start() throws IOException {
		Files.createDirectories(root);
		log.warn("Files are kept in the local folder {} (nexhome.storage.provider=local); set STORAGE_PROVIDER=r2 (or s3) "
				+ "with the bucket's details to use cloud storage.", root);
	}

	@Override
	public String providerName() {
		return PROVIDER;
	}

	@Override
	public String bucketName() {
		return null;
	}

	@Override
	public void put(String key, byte[] data, String contentType) {
		Path target = pathOf(key);
		try {
			Files.createDirectories(target.getParent());
			// Written beside it first, so a half-written file is never read
			Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
			Files.write(temporary, data);
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e) {
			log.error("Could not store {} in {}", key, root, e);
			throw new ServiceUnavailableException("We could not store the file right now; please try again shortly.");
		}
	}

	@Override
	public byte[] get(String key) {
		try {
			return Files.readAllBytes(pathOf(key));
		}
		catch (NoSuchFileException e) {
			throw new ResourceNotFoundException("The file is no longer in storage.");
		}
		catch (IOException e) {
			log.error("Could not read {} from {}", key, root, e);
			throw new ServiceUnavailableException("We could not read the file right now; please try again shortly.");
		}
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(pathOf(key));
		}
		catch (IOException e) {
			log.warn("Could not delete {} from {}", key, root, e);
		}
	}

	@Override
	public String signedUrl(String key, Duration validFor, String fileName, String contentType) {
		return null;
	}

	/** The key's path, never outside the root. */
	private Path pathOf(String key) {
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root) || path.equals(root)) {
			throw new IllegalArgumentException("Storage key outside the storage folder: " + key);
		}
		return path;
	}
}
