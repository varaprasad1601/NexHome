package com.nexhome.app.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeFile;

@Repository
public interface NexHomeFileRepository extends JpaRepository<NexHomeFile, Long>, JpaSpecificationExecutor<NexHomeFile> {

	Optional<NexHomeFile> findByFileUid(UUID fileUid);

	/** The files a record uses (e.g. a user's profile photo), to replace them. */
	List<NexHomeFile> findByReferenceTypeAndReferenceIdAndFileStatus(String referenceType, Long referenceId,
			String fileStatus);

	/** Uploads never attached, for the cleanup. */
	List<NexHomeFile> findTop200ByFileStatusAndCreationDateBefore(String fileStatus, Instant before);

	/** The admin list: only the filters given; search on the original name and public id. */
	static Specification<NexHomeFile> fileSpec(String purpose, String status, String search) {
		return (root, query, cb) -> {
			List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
			if (purpose != null) {
				predicates.add(cb.equal(root.get("purpose"), purpose));
			}
			if (status != null) {
				predicates.add(cb.equal(root.get("fileStatus"), status));
			}
			if (search != null) {
				String pattern = NexHomeSearch.containsPattern(search);
				predicates.add(cb.or(cb.like(cb.lower(root.get("originalName")), pattern, '\\'),
						cb.like(cb.lower(root.get("publicId")), pattern, '\\')));
			}
			return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
		};
	}
}
