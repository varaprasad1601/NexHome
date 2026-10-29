package com.nexhome.app.dto;

/** How many users the notification went to. */
public record NexHomeNotificationSendResponse(
		String audience,
		int recipients) {
}
