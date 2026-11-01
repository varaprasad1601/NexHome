package com.nexhome.app.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeFileContent;
import com.nexhome.app.dto.NexHomeFileDownloadResponse;
import com.nexhome.app.dto.NexHomeFileResponse;
import com.nexhome.app.dto.NexHomePageResponse;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeText;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeFile;
import com.nexhome.app.model.NexHomeLookupCodes.FilePurpose;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomePartnerDocument;
import com.nexhome.app.model.NexHomeSupportTicketAttachment;
import com.nexhome.app.repository.NexHomeFileRepository;
import com.nexhome.app.repository.NexHomePartnerDocumentRepository;
import com.nexhome.app.repository.NexHomeSupportTicketAttachmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NexHomeFileServiceImpl implements NexHomeFileService {

	static final String FILE_MANAGE = "FILE_MANAGE";
	private static final String CATALOG_MANAGE = "CATALOG_MANAGE";
	private static final String PARTNER_ONBOARDING_MANAGE = "PARTNER_ONBOARDING_MANAGE";
	private static final String SUPPORT_MANAGE = "SUPPORT_MANAGE";

	private final NexHomeFileRepository fileRepository;
	private final NexHomePartnerDocumentRepository partnerDocumentRepository;
	private final NexHomeSupportTicketAttachmentRepository attachmentRepository;
	private final NexHomeFiles files;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeAuditUsers auditUsers;
	private final NexHomeAuditRecorder auditRecorder;

	@Override
	@Transactional
	public NexHomeFileResponse upload(String purpose, String fileName, byte[] data) {
		String code = lookupService.validateCode(Types.FILE_PURPOSE, purpose, "Purpose");
		if (FilePurpose.SERVICE_IMAGE.equals(code)) {
			throw new InvalidRequestException("Service images are added with POST /api/catalog/services/images/add.");
		}
		if (FilePurpose.CATEGORY_IMAGE.equals(code) && !permissionService.can(CATALOG_MANAGE, NexHomeAccess.WRITE.name())) {
			throw new AccessDeniedException("You do not have permission to upload category images.");
		}
		NexHomeFile file = files.store(code, fileName, data, permissionService.currentUserId());
		return toDto(file, auditUsers.people(List.of(file.getCreatedBy())));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeFileResponse getFile(Long fileId) {
		NexHomeFile file = findFile(fileId);
		if (NexHomeFiles.DELETED.equals(file.getFileStatus())
				&& !permissionService.can(FILE_MANAGE, NexHomeAccess.READ.name())) {
			throw new ResourceNotFoundException("File does not exist.");
		}
		requireCanSee(file);
		return toDto(file, auditUsers.people(java.util.Collections.singletonList(file.getCreatedBy())));
	}

	@Override
	@Transactional
	public NexHomeFileDownloadResponse getDownloadUrl(Long fileId) {
		NexHomeFile file = findActiveFile(fileId);
		Long actingUserId = permissionService.currentUserId();
		Long ownerId = requireCanSee(file);
		// Someone other than its owner opening a private file (a partner's ID, a customer's attachment) is on record
		if (NexHomeFiles.PRIVATE.equals(file.getVisibility()) && !Objects.equals(ownerId, actingUserId)) {
			auditRecorder.record("FILE_OPENED", file.getReferenceType() == null ? "FILE" : file.getReferenceType(),
					file.getReferenceId() == null ? file.getId() : file.getReferenceId(), null,
					NexHomeAuditRecorder.values("file", file.getPublicId(), "name", file.getOriginalName()),
					actingUserId);
		}
		NexHomeFiles.Download download = files.downloadUrl(file);
		return new NexHomeFileDownloadResponse(file.getId(), download.url(), download.expiresAt(),
				file.getOriginalName(), file.getContentType());
	}

	@Override
	@Transactional
	public void deleteFile(Long fileId) {
		NexHomeFile file = findActiveFile(fileId);
		Long actingUserId = permissionService.currentUserId();
		if (!Objects.equals(file.getCreatedBy(), actingUserId)
				&& !permissionService.can(FILE_MANAGE, NexHomeAccess.DELETE.name())) {
			throw new AccessDeniedException("You can only remove files you uploaded.");
		}
		if (NexHomeFiles.ATTACHED.equals(file.getFileStatus())) {
			throw new InvalidRequestException("The file is in use; remove it where it is used.");
		}
		files.release(file, actingUserId);
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomePageResponse<NexHomeFileResponse> getFiles(Integer page, Integer size, String purpose, String status,
			String search) {
		PageRequest pageRequest = NexHomePaging.newestFirst(page, size);
		String purposeCode = NexHomeText.trimToNull(purpose) == null ? null
				: lookupService.validateCode(Types.FILE_PURPOSE, purpose, "Purpose");
		String statusCode = NexHomeText.upperCase(NexHomeText.trimToNull(status));
		if (statusCode != null && !List.of(NexHomeFiles.UPLOADED, NexHomeFiles.ATTACHED, NexHomeFiles.DELETED)
				.contains(statusCode)) {
			throw new InvalidRequestException("Status must be one of: UPLOADED, ATTACHED, DELETED.");
		}
		Page<NexHomeFile> found = fileRepository.findAll(
				NexHomeFileRepository.fileSpec(purposeCode, statusCode, NexHomePaging.searchText(search)), pageRequest);
		Map<Long, NexHomeAuditUsers.Person> people = auditUsers.people(
				found.stream().map(NexHomeFile::getCreatedBy).filter(Objects::nonNull).toList());
		return NexHomePaging.toPageResponse(pageRequest, found,
				found.stream().map(file -> toDto(file, people)).toList());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeFileContent getPublicContent(UUID fileUid) {
		NexHomeFile file = fileRepository.findByFileUid(fileUid)
				.filter(found -> NexHomeFiles.PUBLIC.equals(found.getVisibility()))
				.filter(found -> !NexHomeFiles.DELETED.equals(found.getFileStatus()))
				.orElseThrow(() -> new ResourceNotFoundException("File does not exist."));
		return new NexHomeFileContent(file.getOriginalName(), file.getContentType(), files.read(file));
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeFileContent getSignedContent(UUID fileUid, long expires, String signature) {
		if (!files.validLink(fileUid, expires, signature)) {
			throw new AccessDeniedException("The link is invalid or has expired; open the file again.");
		}
		NexHomeFile file = fileRepository.findByFileUid(fileUid)
				.filter(found -> !NexHomeFiles.DELETED.equals(found.getFileStatus()))
				.orElseThrow(() -> new ResourceNotFoundException("File does not exist."));
		return new NexHomeFileContent(file.getOriginalName(), file.getContentType(), files.read(file));
	}

	@Override
	@Transactional
	public int removeUnattachedUploads() {
		int removed = files.removeUnattached();
		if (removed > 0) {
			log.info("Removed {} uploads that were never used", removed);
		}
		return removed;
	}

	/**
	 * Who may see a file: anyone for a PUBLIC one; FILE_MANAGE readers; an upload's uploader; a partner document's
	 * partner and the onboarding staff; a ticket attachment's requester (not on an internal note) and the support
	 * staff. Returns the file's owner (the partner's or requester's user id, else the uploader), for the audit.
	 */
	private Long requireCanSee(NexHomeFile file) {
		Long actingUserId = permissionService.currentUserId();
		Long ownerId = file.getCreatedBy();
		boolean allowed = NexHomeFiles.PUBLIC.equals(file.getVisibility())
				|| permissionService.can(FILE_MANAGE, NexHomeAccess.READ.name())
				|| Objects.equals(file.getCreatedBy(), actingUserId) && NexHomeFiles.UPLOADED.equals(file.getFileStatus());
		if (NexHomeFiles.PARTNER_DOCUMENT.equals(file.getReferenceType())) {
			NexHomePartnerDocument document = partnerDocumentRepository.findById(file.getReferenceId()).orElse(null);
			if (document != null) {
				ownerId = document.getPartner().getUser().getId();
				allowed = allowed || Objects.equals(ownerId, actingUserId)
						|| permissionService.can(PARTNER_ONBOARDING_MANAGE, NexHomeAccess.READ.name());
			}
		}
		else if (NexHomeFiles.SUPPORT_ATTACHMENT.equals(file.getReferenceType())) {
			NexHomeSupportTicketAttachment attachment = attachmentRepository.findById(file.getReferenceId())
					.orElse(null);
			if (attachment != null) {
				ownerId = attachment.getTicket().getRequester().getId();
				boolean internal = attachment.getSupportMessage() != null
						&& attachment.getSupportMessage().isInternalNote();
				allowed = allowed || Objects.equals(ownerId, actingUserId) && !internal
						|| permissionService.can(SUPPORT_MANAGE, NexHomeAccess.READ.name());
			}
		}
		if (!allowed) {
			throw new AccessDeniedException("You do not have permission to open this file.");
		}
		return ownerId;
	}

	private NexHomeFile findFile(Long fileId) {
		return fileRepository.findById(fileId).orElseThrow(() -> new ResourceNotFoundException("File does not exist."));
	}

	private NexHomeFile findActiveFile(Long fileId) {
		NexHomeFile file = findFile(fileId);
		if (NexHomeFiles.DELETED.equals(file.getFileStatus())) {
			throw new ResourceNotFoundException("File does not exist.");
		}
		return file;
	}

	private static NexHomeFileResponse toDto(NexHomeFile file, Map<Long, NexHomeAuditUsers.Person> people) {
		NexHomeAuditUsers.Person creator = file.getCreatedBy() == null ? null : people.get(file.getCreatedBy());
		return new NexHomeFileResponse(file.getId(), file.getPublicId(), file.getPurpose(), file.getVisibility(),
				file.getOriginalName(), file.getContentType(), file.getFileSize(), file.getFileStatus(),
				file.getReferenceType(), file.getReferenceId(),
				NexHomeFiles.PUBLIC.equals(file.getVisibility()) ? NexHomeFiles.publicPath(file) : null,
				file.getCreatedBy(), creator == null ? null : creator.name(), file.getCreationDate());
	}
}
