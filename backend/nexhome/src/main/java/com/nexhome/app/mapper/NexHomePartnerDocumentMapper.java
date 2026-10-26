package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.trimToNull;
import static com.nexhome.app.mapper.NexHomeText.upperCase;

import org.springframework.stereotype.Component;

import com.nexhome.app.config.NexHomeFieldEncryptor;
import com.nexhome.app.dto.NexHomePartnerDocumentCreateRequest;
import com.nexhome.app.dto.NexHomePartnerDocumentResponse;
import com.nexhome.app.dto.NexHomePartnerDocumentUpdateRequest;
import com.nexhome.app.model.NexHomeLookupCodes.VerificationStatus;
import com.nexhome.app.model.NexHomePartnerDocument;
import com.nexhome.app.model.NexHomePartnerProfile;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NexHomePartnerDocumentMapper {

	private final NexHomeFieldEncryptor encryptor;

	/** A new document is PENDING verification; its number is upper-cased and encrypted. */
	public NexHomePartnerDocument toPartnerDocumentEntity(NexHomePartnerDocumentCreateRequest request,
			NexHomePartnerProfile partner, String documentType, Long actingUserId) {
		NexHomePartnerDocument document = new NexHomePartnerDocument();
		document.setPartner(partner);
		document.setDocumentType(documentType);
		document.setDocumentNumberEncrypted(encryptor.encrypt(upperCase(request.documentNumber())));
		document.setFileStorageKey(trimToNull(request.fileStorageKey()));
		document.setExpiryDate(request.expiryDate());
		document.setVerificationStatus(VerificationStatus.PENDING);
		document.setCreatedBy(actingUserId);
		document.setUpdatedBy(actingUserId);
		return document;
	}

	/**
	 * Applies an update: a null field keeps its current value and an empty document number clears it. The document
	 * goes back to PENDING verification and loses the previous decision.
	 */
	public void updatePartnerDocumentEntity(NexHomePartnerDocument document,
			NexHomePartnerDocumentUpdateRequest request, Long actingUserId) {
		if (request.documentNumber() != null) {
			document.setDocumentNumberEncrypted(encryptor.encrypt(upperCase(request.documentNumber())));
		}
		if (request.fileStorageKey() != null) {
			document.setFileStorageKey(trimToNull(request.fileStorageKey()));
		}
		if (request.expiryDate() != null) {
			document.setExpiryDate(request.expiryDate());
		}
		document.setVerificationStatus(VerificationStatus.PENDING);
		document.setVerifiedBy(null);
		document.setVerifiedAt(null);
		document.setRejectionReason(null);
		document.setUpdatedBy(actingUserId);
	}

	public NexHomePartnerDocumentResponse toPartnerDocumentDto(NexHomePartnerDocument document) {
		return new NexHomePartnerDocumentResponse(
				document.getId(),
				document.getPartner().getId(),
				document.getDocumentType(),
				NexHomeFieldEncryptor.mask(encryptor.decrypt(document.getDocumentNumberEncrypted())),
				document.getFileStorageKey(),
				document.getVerificationStatus(),
				document.getVerifiedBy(),
				document.getVerifiedAt(),
				document.getExpiryDate(),
				document.getRejectionReason(),
				document.getCreationDate());
	}
}
