package com.nexhome.app.dto;

import jakarta.validation.constraints.NotNull;

/** Pay out a partner's wallet (PAYOUT_MANAGE write). */
public record NexHomeWalletSettleRequest(@NotNull(message = "Partner profile id is required") Long partnerProfileId) {
}
