package com.nexhome.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nexhome.app.model.NexHomeBookingTimeLog;

class NexHomeBookingServiceImplTest {

	private static final Instant T0 = Instant.parse("2026-11-02T04:00:00Z");

	@Test
	void workedTimeLeavesOutPausesAndRoundsUpToTheMinute() {
		// 10 minutes, a 20 minute pause, then 5 minutes 1 second
		List<NexHomeBookingTimeLog> logs = List.of(event("START", 0), event("PAUSE", 600), event("RESUME", 1800),
				event("STOP", 2101));

		assertThat(NexHomeBookingServiceImpl.workedMinutes(logs)).isEqualTo(16);
	}

	@Test
	void noTimerMeansNoTime() {
		assertThat(NexHomeBookingServiceImpl.workedMinutes(List.of())).isZero();
		// A pause without a start counts nothing
		assertThat(NexHomeBookingServiceImpl.workedMinutes(List.of(event("PAUSE", 60)))).isZero();
	}

	@Test
	void aRunningTimerCountsUpToNow() {
		NexHomeBookingTimeLog start = new NexHomeBookingTimeLog();
		start.setEventType("START");
		start.setEventAt(Instant.now().minusSeconds(125));

		assertThat(NexHomeBookingServiceImpl.workedMinutes(List.of(start))).isEqualTo(3);
	}

	private static NexHomeBookingTimeLog event(String type, long secondsAfterStart) {
		NexHomeBookingTimeLog log = new NexHomeBookingTimeLog();
		log.setEventType(type);
		log.setEventAt(T0.plusSeconds(secondsAfterStart));
		return log;
	}
}
