package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.trimToNull;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeServiceMediaCreateRequest;
import com.nexhome.app.dto.NexHomeServiceMediaResponse;
import com.nexhome.app.dto.NexHomeServiceMediaUpdateRequest;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceMedia;

@Component
public class NexHomeServiceMediaMapper {

	/** The media type is validated by the service beforehand. */
	public NexHomeServiceMedia toServiceMediaEntity(NexHomeServiceMediaCreateRequest request, NexHomeService service,
			Long actingUserId) {
		NexHomeServiceMedia media = new NexHomeServiceMedia();
		media.setService(service);
		media.setMediaType(trimToNull(request.mediaType()));
		media.setStorageKey(trimToNull(request.storageKey()));
		media.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
		media.setActive(request.isActive() == null || request.isActive());
		media.setCreatedBy(actingUserId);
		media.setUpdatedBy(actingUserId);
		return media;
	}

	/** Applies an update: a null field keeps its current value. The service never changes. */
	public void updateServiceMediaEntity(NexHomeServiceMedia media, NexHomeServiceMediaUpdateRequest request,
			Long actingUserId) {
		if (request.mediaType() != null) {
			media.setMediaType(trimToNull(request.mediaType()));
		}
		if (request.storageKey() != null) {
			media.setStorageKey(trimToNull(request.storageKey()));
		}
		if (request.displayOrder() != null) {
			media.setDisplayOrder(request.displayOrder());
		}
		if (request.isActive() != null) {
			media.setActive(request.isActive());
		}
		media.setUpdatedBy(actingUserId);
	}

	public NexHomeServiceMediaResponse toServiceMediaDto(NexHomeServiceMedia media) {
		return new NexHomeServiceMediaResponse(
				media.getId(),
				media.getService().getId(),
				media.getMediaType(),
				media.getStorageKey(),
				media.getDisplayOrder(),
				media.isActive(),
				media.getCreationDate());
	}
}
