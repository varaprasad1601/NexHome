package com.nexhome.app.dto;

/**
 * What the signed-in user may do with one function: the union of the grants of all their active roles.
 */
public record NexHomeFunctionAccessResponse(
		String functionModule,
		String functionCode,
		boolean canRead,
		boolean canWrite,
		boolean canDelete) {
}
