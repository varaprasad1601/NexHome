package com.nexhome.app.mapper;

import static com.nexhome.app.mapper.NexHomeText.trimToNull;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomePolicyDocumentCreateRequest;
import com.nexhome.app.dto.NexHomePolicyDocumentResponse;
import com.nexhome.app.dto.NexHomePolicyDocumentUpdateRequest;
import com.nexhome.app.model.NexHomePolicyDocument;

@Component
public class NexHomePolicyDocumentMapper {

	/** A new version is unpublished. The audience is validated by the service beforehand. */
	public NexHomePolicyDocument toPolicyDocumentEntity(NexHomePolicyDocumentCreateRequest request,
			Long actingUserId) {
		NexHomePolicyDocument policy = new NexHomePolicyDocument();
		policy.setPolicyKey(trimToNull(request.policyKey()));
		policy.setTitle(trimToNull(request.title()));
		policy.setAudience(trimToNull(request.audience()));
		policy.setVersion(trimToNull(request.version()));
		// The text is kept exactly as written, apart from surrounding blank lines
		policy.setContent(contentOf(request.content()));
		policy.setEffectiveFrom(request.effectiveFrom());
		policy.setPublished(false);
		policy.setCreatedBy(actingUserId);
		policy.setUpdatedBy(actingUserId);
		return policy;
	}

	/** Applies an update of an unpublished version: a null field keeps its current value. */
	public void updatePolicyDocumentEntity(NexHomePolicyDocument policy, NexHomePolicyDocumentUpdateRequest request,
			Long actingUserId) {
		if (request.title() != null) {
			policy.setTitle(trimToNull(request.title()));
		}
		if (request.audience() != null) {
			policy.setAudience(trimToNull(request.audience()));
		}
		if (request.content() != null) {
			policy.setContent(contentOf(request.content()));
		}
		if (request.effectiveFrom() != null) {
			policy.setEffectiveFrom(request.effectiveFrom());
		}
		policy.setUpdatedBy(actingUserId);
	}

	public NexHomePolicyDocumentResponse toPolicyDocumentDto(NexHomePolicyDocument policy) {
		return new NexHomePolicyDocumentResponse(
				policy.getId(),
				policy.getPolicyKey(),
				policy.getTitle(),
				policy.getAudience(),
				policy.getVersion(),
				policy.getContent(),
				policy.getEffectiveFrom(),
				policy.getPublishedAt(),
				policy.isPublished(),
				policy.getCreationDate());
	}

	/**
	 * The policy text as formatted HTML (p, b, i, u, lists; {@link NexHomeRichText}). Plain text (no tags) keeps its
	 * lines as paragraphs. Text that is empty once cleaned is 400.
	 */
	static String contentOf(String content) {
		String text = content == null ? "" : content.strip();
		if (!text.isEmpty() && !text.contains("<")) {
			StringBuilder paragraphs = new StringBuilder();
			for (String line : text.split("\\R+")) {
				if (!line.isBlank()) {
					paragraphs.append("<p>").append(org.jsoup.nodes.Entities.escape(line.strip())).append("</p>");
				}
			}
			text = paragraphs.toString();
		}
		String cleaned = NexHomeRichText.clean(text);
		if (cleaned == null) {
			throw new com.nexhome.app.exception.InvalidRequestException("Policy text cannot be blank.");
		}
		return cleaned;
	}
}
