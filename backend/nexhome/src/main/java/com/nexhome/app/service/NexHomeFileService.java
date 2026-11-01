package com.nexhome.app.service;

import java.util.UUID;

import com.nexhome.app.dto.NexHomeFileContent;
import com.nexhome.app.dto.NexHomeFileDownloadResponse;
import com.nexhome.app.dto.NexHomeFileResponse;
import com.nexhome.app.dto.NexHomePageResponse;

/**
 * Uploading files and reaching them (V47). A file is uploaded first and then given to what uses it by that feature's
 * own request ({@code fileId}: a partner document, a ticket attachment, a profile photo, a category image).
 */
public interface NexHomeFileService {

	/** Checks and keeps an upload for {@code purpose} (FILE_PURPOSE); unused uploads are removed after a day. */
	NexHomeFileResponse upload(String purpose, String fileName, byte[] data);

	NexHomeFileResponse getFile(Long fileId);

	/** A short-lived link to the file, for those who may see it; opening a private file of someone else is audited. */
	NexHomeFileDownloadResponse getDownloadUrl(Long fileId);

	/** Removes an upload that nothing uses yet: its uploader, or FILE_MANAGE delete. */
	void deleteFile(Long fileId);

	NexHomePageResponse<NexHomeFileResponse> getFiles(Integer page, Integer size, String purpose, String status,
			String search);

	/** A PUBLIC file's bytes, by its unguessable id. */
	NexHomeFileContent getPublicContent(UUID fileUid);

	/** A file's bytes through a link the application signed (local storage). */
	NexHomeFileContent getSignedContent(UUID fileUid, long expires, String signature);

	/** Removes uploads never attached (the scheduled cleanup); returns how many. */
	int removeUnattachedUploads();
}
