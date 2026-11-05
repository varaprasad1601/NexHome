package com.nexhome.app.service;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * No push provider ({@code nexhome.push.provider=log}, the default): nothing is sent, notifications stay in-app. The
 * apps still register their phones, so switching to {@code fcm} reaches them at once.
 */
@Component
@ConditionalOnProperty(name = "nexhome.push.provider", havingValue = "log", matchIfMissing = true)
@Slf4j
public class NexHomeLogPushSender implements NexHomePushSender {

	@PostConstruct
	void warn() {
		log.warn("No push provider (nexhome.push.provider=log): notifications are not pushed to phones.");
	}

	@Override
	public boolean enabled() {
		return false;
	}

	@Override
	public String send(String deviceToken, String title, String body, Map<String, String> data, boolean high) {
		return null;
	}
}
