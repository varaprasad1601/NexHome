package com.nexhome.app.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeSupportTicket;

@Repository
public interface NexHomeSupportTicketRepository extends JpaRepository<NexHomeSupportTicket, Long>,
		JpaSpecificationExecutor<NexHomeSupportTicket> {

	/** A page of tickets with their requesters, assignees and bookings in the same query. */
	@Override
	@EntityGraph(attributePaths = { "requester", "assignedTo", "booking" })
	Page<NexHomeSupportTicket> findAll(Specification<NexHomeSupportTicket> spec, Pageable pageable);

	/** Tickets by status: [status, count]. */
	@Query("select t.status, count(t) from NexHomeSupportTicket t group by t.status")
	List<Object[]> countByStatus();

	/** Open tickets (not RESOLVED or CLOSED) that are URGENT or HIGH. */
	@Query("select count(t) from NexHomeSupportTicket t where t.status not in ('RESOLVED', 'CLOSED') "
			+ "and t.priority in ('URGENT', 'HIGH')")
	long countOpenHighPriority();

	/** Open tickets nobody is handling yet. */
	@Query("select count(t) from NexHomeSupportTicket t where t.status not in ('RESOLVED', 'CLOSED') "
			+ "and t.assignedTo is null")
	long countOpenUnassigned();

	/**
	 * The console's ticket list: only the filters given (no null checks in JPQL). {@code search} (trimmed, lower case)
	 * matches the ticket id and the subject.
	 */
	static Specification<NexHomeSupportTicket> ticketSpec(String status, String priority, String category,
			Long assignedTo, Long requesterId, Long bookingId, String search) {
		Specification<NexHomeSupportTicket> spec = (ticket, query, cb) -> cb.conjunction();
		if (status != null) {
			spec = spec.and((ticket, query, cb) -> cb.equal(ticket.get("status"), status));
		}
		if (priority != null) {
			spec = spec.and((ticket, query, cb) -> cb.equal(ticket.get("priority"), priority));
		}
		if (category != null) {
			spec = spec.and((ticket, query, cb) -> cb.equal(ticket.get("category"), category));
		}
		if (assignedTo != null) {
			spec = spec.and((ticket, query, cb) -> cb.equal(ticket.get("assignedTo").get("id"), assignedTo));
		}
		if (requesterId != null) {
			spec = spec.and((ticket, query, cb) -> cb.equal(ticket.get("requester").get("id"), requesterId));
		}
		if (bookingId != null) {
			spec = spec.and((ticket, query, cb) -> cb.equal(ticket.get("booking").get("id"), bookingId));
		}
		if (search != null) {
			String pattern = NexHomeSearch.containsPattern(search);
			spec = spec.and((ticket, query, cb) -> cb.or(
					cb.like(cb.lower(ticket.get("publicId")), pattern, '\\'),
					cb.like(cb.lower(ticket.get("subject")), pattern, '\\')));
		}
		return spec;
	}
}
