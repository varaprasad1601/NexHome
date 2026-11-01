package com.nexhome.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.config.NexHomeSecurityProperties;
import com.nexhome.app.mapper.NexHomeAuthRefreshTokenMapper;
import com.nexhome.app.mapper.NexHomeUserMapper;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAuthRefreshTokenRepository;
import com.nexhome.app.repository.NexHomeRoleRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * File storage end to end against the dev database (rolled back) and the configured storage (local by default; what
 * a test stores is removed again when its transaction rolls back): uploads checked by their bytes and size, partner
 * documents opened by the partner and staff only (staff openings audited) through signed links, profile photos
 * served publicly and replaced, ticket attachments (internal notes' files hidden from the requester), service images
 * read from storage, removing unused uploads, and the admin list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class NexHomeFileFlowTest {

	private static final byte[] PDF = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H',
			'D', 'R' };
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I',
			'F' };

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JwtEncoder jwtEncoder;
	@Autowired
	private NexHomeSecurityProperties securityProperties;
	@Autowired
	private NexHomeRoleRepository roleRepository;
	@Autowired
	private NexHomeUserRepository userRepository;
	@Autowired
	private NexHomeUserRoleRepository userRoleRepository;
	@Autowired
	private NexHomeUserMapper userMapper;
	@Autowired
	private NexHomeAuthRefreshTokenRepository refreshTokenRepository;
	@Autowired
	private NexHomeAuthRefreshTokenMapper refreshTokenMapper;
	@Autowired
	private EntityManager entityManager;

	@Test
	void partnerDocumentsOpenOnlyForThePartnerAndStaffThroughSignedLinks() throws Exception {
		NexHomeUser partnerUser = newUser("PARTNER");
		String partner = accessToken(partnerUser.getId());
		String otherPartner = accessToken(newUser("PARTNER").getId());
		String staff = accessToken(newUser("SUPER_USER").getId());

		// Checked by the bytes and the purpose's limit, never the name or the client's type
		upload(partner, "PARTNER_DOCUMENT", "id.pdf", "GIF89a".getBytes(StandardCharsets.US_ASCII))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("A document must be a PDF, JPEG or PNG file."));
		upload(partner, "PROFILE_PHOTO", "big.png", Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("A profile photo must be at most 2 MB."));
		upload(partner, "SHOPPING_LIST", "a.pdf", PDF)
				.andExpect(status().isBadRequest());
		upload(partner, "CATEGORY_IMAGE", "a.png", PNG)
				.andExpect(status().isForbidden());

		JsonNode file = json(upload(partner, "PARTNER_DOCUMENT", "C:\\scans\\aadhaar.pdf", PDF)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalName").value("aadhaar.pdf"))
				.andExpect(jsonPath("$.contentType").value("application/pdf"))
				.andExpect(jsonPath("$.visibility").value("PRIVATE"))
				.andExpect(jsonPath("$.fileStatus").value("UPLOADED"))
				.andExpect(jsonPath("$.url").doesNotExist()));
		long fileId = file.get("fileId").asLong();

		long profileId = json(perform(post("/api/partners/profiles/create"), partner,
				"{\"userId\": %d, \"firstName\": \"Ravi\", \"lastName\": \"Teja\"}".formatted(partnerUser.getId()))
				.andExpect(status().isCreated())).get("partnerProfileId").asLong();
		// Another account cannot use the partner's upload
		perform(post("/api/partners/documents/add"), staff, """
				{"partnerProfileId": %d, "documentType": "GOVERNMENT_ID", "idType": "AADHAAR", "fileId": %d}"""
				.formatted(profileId, fileId))
				.andExpect(status().isForbidden());
		long documentId = json(perform(post("/api/partners/documents/add"), partner, """
				{"partnerProfileId": %d, "documentType": "GOVERNMENT_ID", "idType": "AADHAAR", "fileId": %d}"""
				.formatted(profileId, fileId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fileId").value(fileId))
				.andExpect(jsonPath("$.fileName").value("aadhaar.pdf"))
				.andExpect(jsonPath("$.fileContentType").value("application/pdf")))
				.get("partnerDocumentId").asLong();
		// A file is used once
		perform(post("/api/partners/documents/add"), partner, """
				{"partnerProfileId": %d, "documentType": "GOVERNMENT_ID", "idType": "PAN", "fileId": %d}"""
				.formatted(profileId, fileId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The file is already in use or was removed; upload it again."));
		perform(delete("/api/files/delete/" + fileId), partner, null)
				.andExpect(status().isBadRequest());

		perform(get("/api/files/download-url/" + fileId), otherPartner, null)
				.andExpect(status().isForbidden());
		String link = json(perform(get("/api/files/download-url/" + fileId), partner, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileName").value("aadhaar.pdf"))).get("url").asString();
		assertThat(link).startsWith("/api/files/content/");
		// The link works without a token, and only as signed
		mockMvc.perform(get(link))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andExpect(content().bytes(PDF))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")));
		mockMvc.perform(get(link.replaceAll("signature=[0-9a-f]", "signature=x")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(link.replaceAll("expires=\\d+", "expires=" + (Instant.now().getEpochSecond() + 86400))))
				.andExpect(status().isForbidden());

		// Staff opening a partner's document is on record; the partner opening their own is not
		perform(get("/api/files/download-url/" + fileId), staff, null).andExpect(status().isOk());
		assertThat(((Number) entityManager.createNativeQuery("select count(*) from nexhome_audit_logs "
				+ "where action = 'FILE_OPENED' and entity_type = 'PARTNER_DOCUMENT' and entity_id = :id")
				.setParameter("id", String.valueOf(documentId)).getSingleResult()).longValue()).isEqualTo(1);

		// A new file replaces the old one, which is removed
		long newFileId = json(upload(partner, "PARTNER_DOCUMENT", "aadhaar-2.jpg", JPEG)
				.andExpect(status().isCreated())).get("fileId").asLong();
		perform(put("/api/partners/documents/update"), partner,
				"{\"partnerDocumentId\": %d, \"fileId\": %d}".formatted(documentId, newFileId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileName").value("aadhaar-2.jpg"));
		perform(get("/api/files/" + fileId), staff, null)
				.andExpect(jsonPath("$.fileStatus").value("DELETED"));
		perform(get("/api/files/download-url/" + fileId), staff, null)
				.andExpect(status().isNotFound());
	}

	@Test
	void profilePhotosArePublicAndReplacedAndUnusedUploadsCanBeRemoved() throws Exception {
		NexHomeUser customerUser = newUser("CUSTOMER");
		String customer = accessToken(customerUser.getId());
		String otherCustomer = accessToken(newUser("CUSTOMER").getId());
		String staff = accessToken(newUser("SUPER_USER").getId());

		long first = json(upload(customer, "PROFILE_PHOTO", "me.png", PNG)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.visibility").value("PUBLIC"))
				.andExpect(jsonPath("$.url").value(Matchers.startsWith("/api/files/public/"))))
				.get("fileId").asLong();
		perform(put("/api/users/photo/update"), otherCustomer,
				"{\"userId\": %d, \"fileId\": %d}".formatted(customerUser.getId(), first))
				.andExpect(status().isForbidden());
		String photoUrl = json(perform(put("/api/users/photo/update"), customer,
				"{\"userId\": %d, \"fileId\": %d}".formatted(customerUser.getId(), first))
				.andExpect(status().isOk())).get("profilePhotoUrl").asString();
		assertThat(photoUrl).startsWith("/api/files/public/");
		mockMvc.perform(get(photoUrl))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("public")))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));

		long second = json(upload(customer, "PROFILE_PHOTO", "me2.jpg", JPEG)
				.andExpect(status().isCreated())).get("fileId").asLong();
		perform(put("/api/users/photo/update"), customer,
				"{\"userId\": %d, \"fileId\": %d}".formatted(customerUser.getId(), second))
				.andExpect(status().isOk());
		mockMvc.perform(get(photoUrl)).andExpect(status().isNotFound());
		perform(put("/api/users/photo/update"), customer, "{\"userId\": %d}".formatted(customerUser.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profilePhotoUrl").doesNotExist());
		perform(get("/api/files/" + second), staff, null)
				.andExpect(jsonPath("$.fileStatus").value("DELETED"));

		// An upload nothing uses: its uploader may remove it, nobody else
		long unused = json(upload(customer, "SUPPORT_ATTACHMENT", "bill.pdf", PDF)
				.andExpect(status().isCreated())).get("fileId").asLong();
		perform(get("/api/files/" + unused), otherCustomer, null).andExpect(status().isForbidden());
		perform(delete("/api/files/delete/" + unused), otherCustomer, null).andExpect(status().isForbidden());
		perform(delete("/api/files/delete/" + unused), customer, null).andExpect(status().isNoContent());

		perform(get("/api/files?purpose=PROFILE_PHOTO&size=5"), customer, null).andExpect(status().isForbidden());
		perform(get("/api/files?purpose=PROFILE_PHOTO&status=DELETED&size=5"), staff, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].purpose").value("PROFILE_PHOTO"));
	}

	@Test
	void ticketAttachmentsAndServiceImagesUseStorage() throws Exception {
		NexHomeUser customerUser = newUser("CUSTOMER");
		String customer = accessToken(customerUser.getId());
		String otherCustomer = accessToken(newUser("CUSTOMER").getId());
		String staff = accessToken(newUser("SUPER_USER").getId());

		long ticketId = json(perform(post("/api/support/tickets/create"), customer, """
				{"category": "OTHER", "subject": "Bill", "description": "The bill is wrong."}""")
				.andExpect(status().isCreated())).get("supportTicketId").asLong();
		long receipt = json(upload(customer, "SUPPORT_ATTACHMENT", "receipt.pdf", PDF)
				.andExpect(status().isCreated())).get("fileId").asLong();
		long receiptAttachment = json(perform(post("/api/support/tickets/attachments/add"), customer,
				"{\"supportTicketId\": %d, \"fileId\": %d}".formatted(ticketId, receipt))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fileName").value("receipt.pdf"))
				.andExpect(jsonPath("$.fileType").value("application/pdf"))).get("attachmentId").asLong();
		assertThat(receiptAttachment).isPositive();
		perform(get("/api/files/download-url/" + receipt), otherCustomer, null).andExpect(status().isForbidden());
		perform(get("/api/files/download-url/" + receipt), customer, null).andExpect(status().isOk());

		// A staff note's file is for staff only
		long noteId = json(perform(post("/api/support/tickets/messages/add"), staff,
				"{\"supportTicketId\": %d, \"message\": \"Checked the ledger\", \"isInternalNote\": true}"
						.formatted(ticketId))
				.andExpect(status().isCreated())).get("messageId").asLong();
		long ledger = json(upload(staff, "SUPPORT_ATTACHMENT", "ledger.png", PNG)
				.andExpect(status().isCreated())).get("fileId").asLong();
		perform(post("/api/support/tickets/attachments/add"), staff,
				"{\"supportTicketId\": %d, \"messageId\": %d, \"fileId\": %d}".formatted(ticketId, noteId, ledger))
				.andExpect(status().isCreated());
		perform(get("/api/support/tickets/" + ticketId), customer, null)
				.andExpect(jsonPath("$.attachments.length()").value(1));
		perform(get("/api/support/tickets/" + ticketId), staff, null)
				.andExpect(jsonPath("$.attachments.length()").value(2));
		perform(get("/api/files/download-url/" + ledger), customer, null).andExpect(status().isForbidden());

		// Service images keep their address; the bytes come from storage
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		long categoryId = json(perform(post("/api/catalog/categories/create"), staff,
				"{\"name\": \"Files %s\"}".formatted(suffix)).andExpect(status().isCreated()))
				.get("serviceCategoryId").asLong();
		long serviceId = json(perform(post("/api/catalog/services/create"), staff, """
				{"categoryId": %d, "name": "Fan Repair %s", "serviceTarget": "APPLIANCE", "pricingType": "FIXED",
				 "basePrice": 300, "minimumCharge": 0, "estimatedDurationMinutes": 60,
				 "platformCommissionType": "PERCENTAGE", "platformCommissionValue": 10, "isActive": true}"""
				.formatted(categoryId, suffix)).andExpect(status().isCreated())).get("serviceId").asLong();
		String imageUrl = json(mockMvc.perform(multipart("/api/catalog/services/images/add")
				.file(new MockMultipartFile("file", "fan.jpg", "image/jpeg", JPEG))
				.param("serviceId", String.valueOf(serviceId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + staff))
				.andExpect(status().isCreated())).get("imageUrl").asString();
		mockMvc.perform(get(imageUrl))
				.andExpect(status().isOk())
				.andExpect(content().bytes(JPEG));
		assertThat(((Number) entityManager.createNativeQuery("select count(*) from nexhome_service_images "
				+ "where service_id = :id and file_id is not null and image_data is null")
				.setParameter("id", serviceId).getSingleResult()).longValue()).isEqualTo(1);

		// A category image becomes the icon
		long icon = json(upload(staff, "CATEGORY_IMAGE", "icon.webp", webp())
				.andExpect(status().isCreated())).get("fileId").asLong();
		perform(put("/api/catalog/categories/update"), staff,
				"{\"serviceCategoryId\": %d, \"iconFileId\": %d}".formatted(categoryId, icon))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.iconUrl").value(Matchers.startsWith("/api/files/public/")));
	}

	private static byte[] webp() {
		byte[] data = new byte[16];
		System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 4);
		System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, data, 8, 4);
		return data;
	}

	private ResultActions upload(String accessToken, String purpose, String fileName, byte[] data) throws Exception {
		return mockMvc.perform(multipart("/api/files/upload")
				.file(new MockMultipartFile("file", fileName, "application/octet-stream", data))
				.param("purpose", purpose)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String accessToken, String body)
			throws Exception {
		if (accessToken != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		}
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private JsonNode json(ResultActions result) throws Exception {
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	/** An ACTIVE account with one role, created inside the test's transaction. */
	private NexHomeUser newUser(String roleCode) {
		NexHomeUser user = userRepository.save(userMapper.toUserEntity("+91", randomMobile(), null, null, 1L));
		userRoleRepository.saveAndFlush(userMapper.toUserRoleEntity(user,
				roleRepository.findByCode(roleCode).orElseThrow(), 1L));
		return user;
	}

	/** A login session and its access token, as the auth service would issue them. */
	private String accessToken(Long userId) {
		Instant now = Instant.now();
		UUID sessionId = UUID.randomUUID();
		refreshTokenRepository.saveAndFlush(refreshTokenMapper.toRefreshTokenEntity(
				userRepository.findById(userId).orElseThrow(), sessionId, UUID.randomUUID().toString().replace("-", "")
						+ UUID.randomUUID().toString().replace("-", ""),
				now.plusSeconds(3600), "JUnit"));
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(securityProperties.jwtIssuer())
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", sessionId.toString())
				.claim("roles", List.of())
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	private static String randomMobile() {
		return "8" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
	}
}
