package com.nexhome.app.mapper;

import java.net.InetAddress;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomePolicyAcceptanceResponse;
import com.nexhome.app.model.NexHomePolicyAcceptance;
import com.nexhome.app.model.NexHomePolicyDocument;
import com.nexhome.app.model.NexHomeUser;

@Component
public class NexHomePolicyAcceptanceMapper {

	/** The acceptance method is validated by the service beforehand. */
	public NexHomePolicyAcceptance toPolicyAcceptanceEntity(NexHomeUser user, NexHomePolicyDocument policy,
			String acceptanceMethod, InetAddress ipAddress) {
		NexHomePolicyAcceptance acceptance = new NexHomePolicyAcceptance();
		acceptance.setUser(user);
		acceptance.setPolicy(policy);
		acceptance.setAcceptedAt(Instant.now());
		acceptance.setAcceptanceMethod(acceptanceMethod);
		acceptance.setIpAddress(ipAddress);
		acceptance.setCreatedBy(user.getId());
		acceptance.setUpdatedBy(user.getId());
		return acceptance;
	}

	public NexHomePolicyAcceptanceResponse toPolicyAcceptanceDto(NexHomePolicyAcceptance acceptance) {
		NexHomePolicyDocument policy = acceptance.getPolicy();
		return new NexHomePolicyAcceptanceResponse(
				acceptance.getId(),
				acceptance.getUser().getId(),
				policy.getId(),
				policy.getPolicyKey(),
				policy.getVersion(),
				policy.getTitle(),
				acceptance.getAcceptedAt(),
				acceptance.getAcceptanceMethod(),
				acceptance.getIpAddress() == null ? null : acceptance.getIpAddress().getHostAddress());
	}
}
