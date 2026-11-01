package com.nexhome.app.service;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexhome.app.model.NexHomeFile;
import com.nexhome.app.model.NexHomeLookupCodes.FilePurpose;
import com.nexhome.app.model.NexHomeServiceImage;
import com.nexhome.app.repository.NexHomeServiceImageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * At startup, moves service images still kept in the database (added before V47) to file storage, one image per
 * transaction: the bytes are stored, the image points to the file and its {@code image_data} is cleared. An image
 * that cannot be moved (storage down) stays in the database and is still served from there; the next start tries
 * again. Image addresses ({@code /api/catalog/services/images/{id}}) do not change.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class NexHomeServiceImageMover implements ApplicationRunner {

	private final NexHomeServiceImageRepository imageRepository;
	private final NexHomeFiles files;
	private final TransactionTemplate transactionTemplate;

	@Override
	public void run(ApplicationArguments args) {
		List<Long> ids = imageRepository.findIdsWithoutFile();
		if (ids.isEmpty()) {
			return;
		}
		int moved = 0;
		for (Long id : ids) {
			try {
				transactionTemplate.executeWithoutResult(status -> move(id));
				moved++;
			}
			catch (RuntimeException e) {
				log.warn("Service image {} could not be moved to file storage; it stays in the database for now", id,
						e);
			}
		}
		log.info("Moved {} of {} service images from the database to file storage", moved, ids.size());
	}

	private void move(Long id) {
		NexHomeServiceImage image = imageRepository.findById(id).orElse(null);
		if (image == null || image.getFile() != null || image.getImageData() == null) {
			return;
		}
		NexHomeFile file = files.store(FilePurpose.SERVICE_IMAGE, image.getFileName(), image.getImageData(),
				image.getCreatedBy());
		files.link(file, NexHomeFiles.SERVICE_IMAGE, image.getId(), image.getCreatedBy());
		image.setFile(file);
		image.setImageData(null);
	}
}
