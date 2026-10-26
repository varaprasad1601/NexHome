package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeService;

@Repository
public interface NexHomeServiceRepository extends JpaRepository<NexHomeService, Long>,
		JpaSpecificationExecutor<NexHomeService> {

	/** Slugs are unique. */
	@EntityGraph(attributePaths = "category")
	Optional<NexHomeService> findBySlug(String slug);

	@EntityGraph(attributePaths = "category")
	Optional<NexHomeService> findWithCategoryById(Long id);

	boolean existsByCategoryId(Long categoryId);

	/** Loads each page's categories in the same query (the list shows the category name). */
	@Override
	@EntityGraph(attributePaths = "category")
	Page<NexHomeService> findAll(Specification<NexHomeService> spec, Pageable pageable);

	/**
	 * Services matching every filter given: category, pricing type, visibility ({@code activeOnly}: the service and
	 * its category are both active) and {@code search} (lower-cased; part of the name or public id). Null filters
	 * are left out of the SQL; see {@link NexHomeUserRepository#findByActiveRole}.
	 */
	default Page<NexHomeService> search(Long categoryId, String pricingType, boolean activeOnly, String search,
			Pageable pageable) {
		Specification<NexHomeService> spec = Specification.unrestricted();
		if (categoryId != null) {
			spec = spec.and((service, query, cb) -> cb.equal(service.get("category").get("id"), categoryId));
		}
		if (pricingType != null) {
			spec = spec.and((service, query, cb) -> cb.equal(service.get("pricingType"), pricingType));
		}
		if (activeOnly) {
			spec = spec.and((service, query, cb) -> cb.and(cb.isTrue(service.get("active")),
					cb.isTrue(service.get("category").get("active"))));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((service, query, cb) -> cb.or(
					cb.like(cb.lower(service.get("name")), pattern, '\\'),
					cb.like(cb.lower(service.get("publicId")), pattern, '\\')));
		}
		return findAll(spec, pageable);
	}
}
