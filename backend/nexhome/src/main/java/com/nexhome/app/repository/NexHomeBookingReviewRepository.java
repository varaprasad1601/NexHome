package com.nexhome.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeBookingReview;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

@Repository
public interface NexHomeBookingReviewRepository extends JpaRepository<NexHomeBookingReview, Long>,
		JpaSpecificationExecutor<NexHomeBookingReview> {

	Optional<NexHomeBookingReview> findByBookingId(Long bookingId);

	boolean existsByBookingId(Long bookingId);

	/** A page of reviews with their bookings, customers and partners in the same query (all are listed). */
	@Override
	@EntityGraph(attributePaths = { "booking", "customer", "partner" })
	Page<NexHomeBookingReview> findAll(Specification<NexHomeBookingReview> spec, Pageable pageable);

	/** Reviews by status: [status, count, average partner rating, average service rating]. */
	@Query("select r.reviewStatus, count(r), avg(r.partnerRating), avg(r.serviceRating) from NexHomeBookingReview r "
			+ "group by r.reviewStatus")
	List<Object[]> countByStatus();

	/** PUBLISHED reviews by partner rating: [rating, count]. */
	@Query("select r.partnerRating, count(r) from NexHomeBookingReview r where r.reviewStatus = 'PUBLISHED' "
			+ "group by r.partnerRating")
	List<Object[]> countPublishedByPartnerRating();

	/** A partner's PUBLISHED reviews: [average partner rating (null without any), count]. */
	@Query("select avg(r.partnerRating), count(r) from NexHomeBookingReview r where r.partner.id = :partnerId "
			+ "and r.reviewStatus = 'PUBLISHED'")
	List<Object[]> partnerRatingOf(@Param("partnerId") Long partnerId);

	/** The partner ratings a customer gave: [average (null without any), count]. */
	@Query("select avg(r.partnerRating), count(r) from NexHomeBookingReview r where r.customer.id = :customerId")
	List<Object[]> ratingsGivenBy(@Param("customerId") Long customerId);

	/** A partner's PUBLISHED reviews by partner rating: [rating, count]. */
	@Query("select r.partnerRating, count(r) from NexHomeBookingReview r where r.partner.id = :partnerId "
			+ "and r.reviewStatus = 'PUBLISHED' group by r.partnerRating")
	List<Object[]> countPublishedOfPartnerByRating(@Param("partnerId") Long partnerId);

	/** The partner ratings a customer gave, by rating: [rating, count]. */
	@Query("select r.partnerRating, count(r) from NexHomeBookingReview r where r.customer.id = :customerId "
			+ "group by r.partnerRating")
	List<Object[]> countGivenByCustomerByRating(@Param("customerId") Long customerId);

	/** PUBLISHED reviews per month (IST), oldest first: the count and the sum of partner ratings. */
	@Query(nativeQuery = true, value = """
			select to_char(r.creation_date at time zone 'Asia/Kolkata', 'YYYY-MM') as month, count(*) as total,
				sum(r.partner_rating) as ratingSum
			from nexhome_booking_reviews r
			where r.review_status = 'PUBLISHED'
			group by 1
			order by 1""")
	List<MonthlyRatings> publishedByMonth();

	/** One row of {@link #publishedByMonth}. */
	interface MonthlyRatings {

		/** {@code YYYY-MM}. */
		String getMonth();

		Long getTotal();

		Long getRatingSum();
	}

	/** The reviews of several bookings (for booking lists). */
	List<NexHomeBookingReview> findByBookingIdIn(Collection<Long> bookingIds);

	/**
	 * The console's review list: only the filters given (no null checks in JPQL). {@code search} (trimmed, lower case)
	 * matches the booking id, the service, the customer's and partner's names and ids, and the text.
	 */
	static Specification<NexHomeBookingReview> reviewSpec(String status, Long partnerId, Long customerId,
			Short rating, String search) {
		Specification<NexHomeBookingReview> spec = (review, query, cb) -> cb.conjunction();
		if (status != null) {
			spec = spec.and((review, query, cb) -> cb.equal(review.get("reviewStatus"), status));
		}
		if (partnerId != null) {
			spec = spec.and((review, query, cb) -> cb.equal(review.get("partner").get("id"), partnerId));
		}
		if (customerId != null) {
			spec = spec.and((review, query, cb) -> cb.equal(review.get("customer").get("id"), customerId));
		}
		if (rating != null) {
			spec = spec.and((review, query, cb) -> cb.equal(review.get("partnerRating"), rating));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((review, query, cb) -> {
				Join<Object, Object> booking = review.join("booking", JoinType.LEFT);
				Join<Object, Object> customer = review.join("customer", JoinType.LEFT);
				Join<Object, Object> partner = review.join("partner", JoinType.LEFT);
				return cb.or(
						cb.like(cb.lower(booking.get("publicId")), pattern, '\\'),
						cb.like(cb.lower(booking.get("serviceNameSnapshot")), pattern, '\\'),
						cb.like(cb.lower(cb.coalesce(review.<String>get("reviewText"), "")), pattern, '\\'),
						cb.like(cb.lower(customer.get("publicId")), pattern, '\\'),
						cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(customer.<String>get("firstName"), ""), " "),
								cb.coalesce(customer.<String>get("lastName"), ""))), pattern, '\\'),
						cb.like(cb.lower(partner.get("publicId")), pattern, '\\'),
						cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(partner.<String>get("firstName"), ""), " "),
								cb.coalesce(partner.<String>get("lastName"), ""))), pattern, '\\'));
			});
		}
		return spec;
	}
}
