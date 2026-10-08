package com.nexhome.app.model;

/**
 * Lifecycle of a user account, stored as text in nexhome_users.user_status.
 * <ol>
 * <li>{@link #SUBMITTED}: created with phone, email and role; no password, inactive.</li>
 * <li>{@link #APPROVED} or {@link #REJECTED}: set by an admin after verifying the user's details.</li>
 * <li>{@link #ACTIVATED}: the approved user has set a password; the account is active and can sign in.</li>
 * </ol>
 */
public enum NexHomeUserStatus {
	SUBMITTED,
	APPROVED,
	REJECTED,
	ACTIVATED
}
