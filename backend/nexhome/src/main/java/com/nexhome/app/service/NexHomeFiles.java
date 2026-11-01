package com.nexhome.app.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.ContentDisposition;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.nexhome.app.config.NexHomeSecurityProperties;
import com.nexhome.app.config.NexHomeStorageProperties;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.model.NexHomeFile;
import com.nexhome.app.model.NexHomeLookupCodes.FilePurpose;
import com.nexhome.app.repository.NexHomeFileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Uploaded files, shared by the features that use them (inside the caller's transaction, like
 * {@code NexHomeSettlement}): {@link #store} checks a file against its purpose's rules (type from its own bytes, size)
 * and keeps it under a random key; {@link #attach} gives an upload to the record that uses it; {@link #release}
 * removes a file (the stored object only once the transaction commits, and an object stored by a transaction that
 * rolls back is removed again). Public files are served at {@code /api/files/public/{fileUid}}; private ones through
 * short-lived links: the storage's own (S3 presigned) or, for local storage, the application's signed
 * {@code /api/files/content/{fileUid}?expires=...&signature=...}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class NexHomeFiles {

	static final String PUBLIC = "PUBLIC";
	static final String PRIVATE = "PRIVATE";
	static final String UPLOADED = "UPLOADED";
	static final String ATTACHED = "ATTACHED";
	static final String DELETED = "DELETED";

	/** What uses an attached file ({@code reference_type}). */
	static final String USER = "USER";
	static final String SERVICE_CATEGORY = "SERVICE_CATEGORY";
	static final String SERVICE_IMAGE = "SERVICE_IMAGE";
	static final String PARTNER_DOCUMENT = "PARTNER_DOCUMENT";
	static final String SUPPORT_ATTACHMENT = "SUPPORT_ATTACHMENT";

	static final String PUBLIC_PATH = "/api/files/public/";
	static final String CONTENT_PATH = "/api/files/content/";

	private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
	private static final long MB = 1024L * 1024L;
	private static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final Set<String> DOCUMENTS = Set.of("application/pdf", "image/jpeg", "image/png");
	private static final Set<String> ATTACHMENTS = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");

	/** Each purpose's visibility, size limit, types, storage folder and the name used in messages. */
	record Rule(String visibility, long maxBytes, Set<String> types, String folder, String noun, String typesText) {
	}

	static final Map<String, Rule> RULES = Map.of(
			FilePurpose.PROFILE_PHOTO, new Rule(PUBLIC, 2 * MB, IMAGES, "profile-photos", "A profile photo",
					"a JPEG, PNG or WebP image"),
			FilePurpose.CATEGORY_IMAGE, new Rule(PUBLIC, 2 * MB, IMAGES, "category-images", "A category image",
					"a JPEG, PNG or WebP image"),
			FilePurpose.SERVICE_IMAGE, new Rule(PUBLIC, 5 * MB, IMAGES, "service-images", "A service image",
					"a JPEG, PNG or WebP image"),
			FilePurpose.PARTNER_DOCUMENT, new Rule(PRIVATE, 10 * MB, DOCUMENTS, "partner-documents", "A document",
					"a PDF, JPEG or PNG file"),
			FilePurpose.SUPPORT_ATTACHMENT, new Rule(PRIVATE, 10 * MB, ATTACHMENTS, "support-attachments",
					"An attachment", "a PDF, JPEG, PNG or WebP file"));

	private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", ".jpg", "image/png", ".png",
			"image/webp", ".webp", "application/pdf", ".pdf");

	/** A download link and when it stops working. */
	record Download(String url, Instant expiresAt) {
	}

	private final NexHomeFileRepository fileRepository;
	private final NexHomeFileStorage storage;
	private final NexHomeStorageProperties storageProperties;
	private final NexHomeSecurityProperties securityProperties;

	/** Checks and keeps an upload (UPLOADED until something uses it). */
	NexHomeFile store(String purpose, String fileName, byte[] data, Long actingUserId) {
		Rule rule = RULES.get(purpose);
		if (rule == null) {
			throw new IllegalArgumentException("No file rules for purpose " + purpose);
		}
		if (data == null || data.length == 0) {
			throw new InvalidRequestException("Choose a file to upload.");
		}
		if (data.length > rule.maxBytes()) {
			throw new InvalidRequestException(rule.noun() + " must be at most " + rule.maxBytes() / MB + " MB.");
		}
		String contentType = detectType(data);
		if (contentType == null || !rule.types().contains(contentType)) {
			throw new InvalidRequestException(rule.noun() + " must be " + rule.typesText() + ".");
		}
		ZonedDateTime now = ZonedDateTime.now(ZONE);
		String key = "%s/%d/%02d/%s%s".formatted(rule.folder(), now.getYear(), now.getMonthValue(), UUID.randomUUID(),
				EXTENSIONS.get(contentType));

		storage.put(key, data, contentType);
		// An object stored by a transaction that then fails would be left behind with no row: remove it again
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status == STATUS_ROLLED_BACK) {
						storage.delete(key);
					}
				}
			});
		}

		NexHomeFile file = new NexHomeFile();
		file.setPurpose(purpose);
		file.setVisibility(rule.visibility());
		file.setStorageProvider(storage.providerName());
		file.setBucketName(storage.bucketName());
		file.setStorageKey(key);
		file.setOriginalName(fileName(fileName, "file" + EXTENSIONS.get(contentType)));
		file.setContentType(contentType);
		file.setFileSize(data.length);
		file.setChecksumSha256(sha256(data));
		file.setFileStatus(UPLOADED);
		file.setCreatedBy(actingUserId);
		file.setUpdatedBy(actingUserId);
		file = fileRepository.saveAndFlush(file);
		log.info("File {} ({}, {} bytes) stored as {}", file.getPublicId(), purpose, data.length, key);
		return file;
	}

	/**
	 * Gives the acting user's upload to the record that uses it: it must have been uploaded for this purpose, by
	 * them, and not be in use yet.
	 */
	NexHomeFile attach(Long fileId, String purpose, String referenceType, Long referenceId, Long actingUserId) {
		NexHomeFile file = usable(fileId, purpose, actingUserId);
		link(file, referenceType, referenceId, actingUserId);
		return file;
	}

	/** The checks of {@link #attach} alone, for a record that needs the file before it has an id. */
	NexHomeFile usable(Long fileId, String purpose, Long actingUserId) {
		NexHomeFile file = fileRepository.findById(fileId)
				.orElseThrow(() -> new ResourceNotFoundException("File does not exist."));
		if (!purpose.equals(file.getPurpose())) {
			throw new InvalidRequestException("The file was uploaded for something else; upload it again here.");
		}
		if (!UPLOADED.equals(file.getFileStatus())) {
			throw new InvalidRequestException("The file is already in use or was removed; upload it again.");
		}
		if (!Objects.equals(file.getCreatedBy(), actingUserId)) {
			throw new AccessDeniedException("You can only use files you uploaded.");
		}
		return file;
	}

	/** Marks a file as used by a record (no checks: for files the application stored itself). */
	void link(NexHomeFile file, String referenceType, Long referenceId, Long actingUserId) {
		file.setFileStatus(ATTACHED);
		file.setReferenceType(referenceType);
		file.setReferenceId(referenceId);
		file.setAttachedAt(Instant.now());
		file.setUpdatedBy(actingUserId);
	}

	/** Removes a file: the row stays DELETED, the stored object goes once the transaction commits. */
	void release(NexHomeFile file, Long actingUserId) {
		if (file == null || DELETED.equals(file.getFileStatus())) {
			return;
		}
		file.setFileStatus(DELETED);
		file.setDeletedAt(Instant.now());
		file.setDeletedBy(actingUserId);
		file.setUpdatedBy(actingUserId);
		String key = file.getStorageKey();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					storage.delete(key);
				}
			});
		}
		else {
			storage.delete(key);
		}
		log.info("File {} removed", file.getPublicId());
	}

	/** Removes the files a record uses, except {@code keepFileId} (e.g. the old profile photo after a new one). */
	void releaseAttached(String referenceType, Long referenceId, Long keepFileId, Long actingUserId) {
		fileRepository.findByReferenceTypeAndReferenceIdAndFileStatus(referenceType, referenceId, ATTACHED).stream()
				.filter(file -> !file.getId().equals(keepFileId))
				.forEach(file -> release(file, actingUserId));
	}

	/** Uploads never attached within {@code nexhome.storage.unattached-hours}: removed (200 at a time). */
	int removeUnattached() {
		Instant before = Instant.now().minus(Duration.ofHours(storageProperties.unattachedHours()));
		List<NexHomeFile> files = fileRepository.findTop200ByFileStatusAndCreationDateBefore(UPLOADED, before);
		files.forEach(file -> release(file, null));
		return files.size();
	}

	byte[] read(NexHomeFile file) {
		return storage.get(file.getStorageKey());
	}

	/** The public address of a PUBLIC file (relative to the API). */
	static String publicPath(NexHomeFile file) {
		return PUBLIC_PATH + file.getFileUid();
	}

	/** A short-lived link to the file: the storage's own when it signs links, else the application's. */
	Download downloadUrl(NexHomeFile file) {
		Duration validFor = Duration.ofMinutes(storageProperties.downloadUrlMinutes());
		Instant expiresAt = Instant.now().plus(validFor);
		String url = storage.signedUrl(file.getStorageKey(), validFor, file.getOriginalName(), file.getContentType());
		if (url == null) {
			long expires = expiresAt.getEpochSecond();
			url = CONTENT_PATH + file.getFileUid() + "?expires=" + expires + "&signature="
					+ signature(file.getFileUid(), expires);
		}
		return new Download(url, expiresAt);
	}

	/** Whether an application-signed link is genuine and still valid. */
	boolean validLink(UUID fileUid, long expires, String signature) {
		return signature != null && expires >= Instant.now().getEpochSecond()
				&& MessageDigest.isEqual(signature(fileUid, expires).getBytes(StandardCharsets.US_ASCII),
						signature.getBytes(StandardCharsets.US_ASCII));
	}

	/** HMAC-SHA256 of the file and expiry with the token signing key (hex). */
	private String signature(UUID fileUid, long expires) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(Base64.getDecoder().decode(securityProperties.jwtSecret()), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(("file-link:" + fileUid + ":" + expires)
					.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Cannot sign file links", e);
		}
	}

	/** The type from the file's first bytes (never the client's content type), or null for anything else. */
	static String detectType(byte[] data) {
		if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
			return "image/jpeg";
		}
		if (startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
			return "image/png";
		}
		if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
			return "image/webp";
		}
		if (startsWith(data, 0, '%', 'P', 'D', 'F', '-')) {
			return "application/pdf";
		}
		return null;
	}

	private static boolean startsWith(byte[] data, int offset, int... expected) {
		if (data.length < offset + expected.length) {
			return false;
		}
		for (int index = 0; index < expected.length; index++) {
			if ((data[offset + index] & 0xFF) != expected[index]) {
				return false;
			}
		}
		return true;
	}

	/** The uploaded file's name without any path or control characters, at most 255 characters. */
	static String fileName(String fileName, String fallback) {
		String name = fileName == null ? "" : fileName.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
		if (name.isEmpty()) {
			return fallback;
		}
		return name.length() <= 255 ? name : name.substring(name.length() - 255);
	}

	/** {@code inline; filename="..."} with the UTF-8 name too, so the browser shows the file under its name. */
	static String inlineDisposition(String fileName) {
		return ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build().toString();
	}

	private static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
