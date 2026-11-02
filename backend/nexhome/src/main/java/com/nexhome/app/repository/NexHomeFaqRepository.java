package com.nexhome.app.repository;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeFaq;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceCategory;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

@Repository
public interface NexHomeFaqRepository extends JpaRepository<NexHomeFaq, Long>, JpaSpecificationExecutor<NexHomeFaq> {

	boolean existsByCategoryId(Long categoryId);

	boolean existsByServiceId(Long serviceId);

	/**
	 * FAQs matching every filter given: audiences (any of), category, service, {@code general} (true: app FAQs, with
	 * neither a category nor a service; false: the others), published flag and {@code search} (lower-cased; part of
	 * the question). {@code activeScopeOnly} leaves out FAQs of an inactive category or service.
	 * Null filters are left out of the SQL; see {@link NexHomeUserRepository#findByActiveRole}.
	 */
	default Page<NexHomeFaq> search(Collection<String> audiences, Long categoryId, Long serviceId, Boolean general,
			Boolean published, String search, boolean activeScopeOnly, Pageable pageable) {
		Specification<NexHomeFaq> spec = Specification.unrestricted();
		if (activeScopeOnly) {
			spec = spec.and((faq, query, cb) -> {
				// Left joins: general FAQs have neither
				Join<NexHomeFaq, NexHomeServiceCategory> category = faq.join("category", JoinType.LEFT);
				Join<NexHomeFaq, NexHomeService> service = faq.join("service", JoinType.LEFT);
				return cb.and(cb.or(cb.isNull(category), cb.isTrue(category.get("active"))),
						cb.or(cb.isNull(service), cb.isTrue(service.get("active"))));
			});
		}
		if (audiences != null) {
			spec = spec.and((faq, query, cb) -> faq.get("audience").in(audiences));
		}
		if (categoryId != null) {
			spec = spec.and((faq, query, cb) -> cb.equal(faq.get("category").get("id"), categoryId));
		}
		if (serviceId != null) {
			spec = spec.and((faq, query, cb) -> cb.equal(faq.get("service").get("id"), serviceId));
		}
		if (general != null) {
			spec = spec.and((faq, query, cb) -> {
				var neither = cb.and(cb.isNull(faq.get("category")), cb.isNull(faq.get("service")));
				return general ? neither : cb.not(neither);
			});
		}
		if (published != null) {
			spec = spec.and((faq, query, cb) -> cb.equal(faq.get("published"), published));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((faq, query, cb) -> cb.like(cb.lower(faq.get("question")), pattern, '\\'));
		}
		return findAll(spec, pageable);
	}
}
