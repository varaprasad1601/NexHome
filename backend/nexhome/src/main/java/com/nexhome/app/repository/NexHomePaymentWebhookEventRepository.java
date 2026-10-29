package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePaymentWebhookEvent;

@Repository
public interface NexHomePaymentWebhookEventRepository extends JpaRepository<NexHomePaymentWebhookEvent, Long> {

	Optional<NexHomePaymentWebhookEvent> findByProviderAndProviderEventId(String provider, String providerEventId);

	/** Newest first. */
	Page<NexHomePaymentWebhookEvent> findAllByOrderByReceivedAtDescIdDesc(Pageable pageable);
}
