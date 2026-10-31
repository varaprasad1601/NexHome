package com.nexhome.app.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * No SMS provider ({@code nexhome.sms.provider=log}, the default): nothing is sent. Under dev the OTP is logged
 * instead ({@code nexhome.otp.log-plaintext}); anywhere else users cannot receive their OTPs, so startup warns.
 */
@Component
@ConditionalOnProperty(name = "nexhome.sms.provider", havingValue = "log", matchIfMissing = true)
@Slf4j
public class NexHomeLogSmsSender implements NexHomeSmsSender {

	@PostConstruct
	void warn() {
		log.warn("No SMS provider (nexhome.sms.provider=log): OTPs are not sent by SMS.");
	}

	@Override
	public String providerName() {
		return null;
	}

	@Override
	public String sendOtp(String countryCode, String mobileNumber, String otp) {
		return null;
	}
}
