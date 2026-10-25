package com.nexhome.app.config;

import java.util.Arrays;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveProfileLogger {

	private final Environment environment;

	@EventListener(ApplicationReadyEvent.class)
	public void logActiveProfiles() {
		String[] active = environment.getActiveProfiles();
		if (active.length == 0) {
			log.warn("No active Spring profile set, using default: {}",
					Arrays.toString(environment.getDefaultProfiles()));
		} else {
			log.info("Application started with active profile(s): {}", Arrays.toString(active));
		}
	}
}
