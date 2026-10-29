package com.nexhome.app.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomePartnerBankAccount;

@Repository
public interface NexHomePartnerBankAccountRepository extends JpaRepository<NexHomePartnerBankAccount, Long> {

	/** The primary account first, then newest first. */
	List<NexHomePartnerBankAccount> findByPartnerIdOrderByPrimaryDescCreationDateDescIdDesc(Long partnerId);

	/** The partner's primary account; unique by the partial index ux_nexhome_partner_bank_accounts_one_primary. */
	Optional<NexHomePartnerBankAccount> findByPartnerIdAndPrimaryTrue(Long partnerId);

	/** The primary accounts of a page of partners, in one query. */
	List<NexHomePartnerBankAccount> findByPartnerIdInAndPrimaryTrue(Collection<Long> partnerIds);
}
