package com.nexhome.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexhome.app.model.NexHomePaymentAttempt;

public interface NexHomePaymentAttemptRepository extends JpaRepository<NexHomePaymentAttempt, Long> {

	/** An order's attempts, newest first (by when they were made, then recorded). */
	List<NexHomePaymentAttempt> findByPaymentOrderIdOrderByAttemptedAtDescIdDesc(Long paymentOrderId);

	Optional<NexHomePaymentAttempt> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

	/** Successful attempts since {@code since} whose settlement is not known yet (V68), oldest first, with the order. */
	@org.springframework.data.jpa.repository.Query("select a from NexHomePaymentAttempt a join fetch a.paymentOrder "
			+ "where a.provider = :provider and a.attemptStatus = 'SUCCESS' and a.settlementId is null "
			+ "and a.attemptedAt >= :since order by a.attemptedAt asc, a.id asc")
	List<NexHomePaymentAttempt> findUnsettled(String provider, java.time.Instant since,
			org.springframework.data.domain.Pageable pageable);

	/** What the gateway has taken in all: charges + tax of the attempts it reported them for. */
	@org.springframework.data.jpa.repository.Query("select coalesce(sum(coalesce(a.gatewayCharge, 0) "
			+ "+ coalesce(a.gatewayTax, 0)), 0) from NexHomePaymentAttempt a where a.chargesRecordedAt is not null")
	java.math.BigDecimal gatewayChargesTotal();
}
