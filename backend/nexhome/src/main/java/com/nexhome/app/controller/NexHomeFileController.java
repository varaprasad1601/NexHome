package com.nexhome.app.controller;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nexhome.app.dto.NexHomeFileContent;
import com.nexhome.app.dto.NexHomeFileDownloadResponse;
import com.nexhome.app.dto.NexHomeFileResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.service.NexHomeFileService;

import lombok.RequiredArgsConstructor;

/**
 * Files (V47): upload, then give the file to what uses it with that feature's own request; private files open
 * through short-lived links, public ones (images) at a fixed address.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class NexHomeFileController {

	private final NexHomeFileService fileService;

	/** Multipart form: {@code purpose} (FILE_PURPOSE) and the {@code file}. */
	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeFileResponse upload(@RequestParam String purpose, @RequestParam MultipartFile file)
			throws IOException {
		return fileService.upload(purpose, file.getOriginalFilename(), file.getBytes());
	}

	@GetMapping
	@PreAuthorize("@permissions.can('FILE_MANAGE', 'READ')")
	public NexHomePageResponse<NexHomeFileResponse> getFiles(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String purpose,
			@RequestParam(required = false) String status, @RequestParam(required = false) String search) {
		return fileService.getFiles(page, size, purpose, status, search);
	}

	@GetMapping("/{fileId}")
	public NexHomeFileResponse getFile(@PathVariable Long fileId) {
		return fileService.getFile(fileId);
	}

	@GetMapping("/download-url/{fileId}")
	public NexHomeFileDownloadResponse getDownloadUrl(@PathVariable Long fileId) {
		return fileService.getDownloadUrl(fileId);
	}

	@DeleteMapping("/delete/{fileId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteFile(@PathVariable Long fileId) {
		fileService.deleteFile(fileId);
	}

	/** A public file (public, by its unguessable id); cached for a day, since a stored file never changes. */
	@GetMapping("/public/{fileUid}")
	public ResponseEntity<byte[]> getPublicContent(@PathVariable UUID fileUid) {
		return serve(fileService.getPublicContent(fileUid), CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
	}

	/** A file through a link the application signed (local storage; public path, the signature is the check). */
	@GetMapping("/content/{fileUid}")
	public ResponseEntity<byte[]> getSignedContent(@PathVariable UUID fileUid, @RequestParam long expires,
			@RequestParam String signature) {
		return serve(fileService.getSignedContent(fileUid, expires, signature), CacheControl.noStore());
	}

	private static ResponseEntity<byte[]> serve(NexHomeFileContent content, CacheControl cacheControl) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(content.contentType()))
				.cacheControl(cacheControl)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						org.springframework.http.ContentDisposition.inline()
								.filename(content.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.body(content.data());
	}
}
