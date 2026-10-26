package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.collapseWhitespace;
import static com.nexhome.app.mapper.NexHomeText.trimToNull;
import static com.nexhome.app.mapper.NexHomeText.upperCase;

import org.springframework.stereotype.Component;

import com.nexhome.app.config.NexHomeFieldEncryptor;
import com.nexhome.app.dto.NexHomePartnerBankAccountCreateRequest;
import com.nexhome.app.dto.NexHomePartnerBankAccountResponse;
import com.nexhome.app.dto.NexHomePartnerBankAccountUpdateRequest;
import com.nexhome.app.model.NexHomeLookupCodes.VerificationStatus;
import com.nexhome.app.model.NexHomePartnerBankAccount;
import com.nexhome.app.model.NexHomePartnerProfile;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NexHomePartnerBankAccountMapper {

	private final NexHomeFieldEncryptor encryptor;

	/**
	 * A new account is PENDING verification. The number is encrypted, with its last four digits kept for display; the
	 * IFSC code is upper-cased. The primary flag is applied by the service.
	 */
	public NexHomePartnerBankAccount toPartnerBankAccountEntity(NexHomePartnerBankAccountCreateRequest request,
			NexHomePartnerProfile partner, Long actingUserId) {
		String accountNumber = request.accountNumber().trim();
		NexHomePartnerBankAccount account = new NexHomePartnerBankAccount();
		account.setPartner(partner);
		account.setAccountHolderName(collapseWhitespace(request.accountHolderName()));
		account.setAccountNumberEncrypted(encryptor.encrypt(accountNumber));
		account.setAccountNumberLast4(accountNumber.substring(accountNumber.length() - 4));
		account.setIfscCode(upperCase(request.ifscCode()));
		account.setBankName(trimToNull(request.bankName()));
		account.setVerificationStatus(VerificationStatus.PENDING);
		account.setCreatedBy(actingUserId);
		account.setUpdatedBy(actingUserId);
		return account;
	}

	/**
	 * Applies an update: a null field keeps its current value and an empty bank name clears it. A new holder name sends
	 * the account back to PENDING verification. The primary flag is applied by the service.
	 */
	public void updatePartnerBankAccountEntity(NexHomePartnerBankAccount account,
			NexHomePartnerBankAccountUpdateRequest request, Long actingUserId) {
		if (request.accountHolderName() != null) {
			String holderName = collapseWhitespace(request.accountHolderName());
			if (!holderName.equals(account.getAccountHolderName())) {
				account.setAccountHolderName(holderName);
				account.setVerificationStatus(VerificationStatus.PENDING);
			}
		}
		if (request.bankName() != null) {
			account.setBankName(trimToNull(request.bankName()));
		}
		account.setUpdatedBy(actingUserId);
	}

	/** Decrypts the stored number, e.g. to recognise an account that is added twice. Never log the result. */
	public String accountNumber(NexHomePartnerBankAccount account) {
		return encryptor.decrypt(account.getAccountNumberEncrypted());
	}

	public NexHomePartnerBankAccountResponse toPartnerBankAccountDto(NexHomePartnerBankAccount account) {
		return new NexHomePartnerBankAccountResponse(
				account.getId(),
				account.getPartner().getId(),
				account.getAccountHolderName(),
				account.getAccountNumberLast4(),
				account.getIfscCode(),
				account.getBankName(),
				account.getVerificationStatus(),
				account.isPrimary(),
				account.getCreationDate());
	}
}
