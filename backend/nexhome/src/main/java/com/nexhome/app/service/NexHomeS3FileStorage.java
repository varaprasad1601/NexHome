package com.nexhome.app.service;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.nexhome.app.config.NexHomeStorageProperties;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.exception.ServiceUnavailableException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Files in a private AWS S3 bucket ({@code nexhome.storage.provider=s3}): objects are encrypted at rest (SSE-S3), the
 * bucket is never public, and private files are downloaded through presigned links that expire. Another
 * S3-compatible store works with {@code endpoint} set; Cloudflare R2 is {@link NexHomeR2FileStorage}.
 */
@Component
@ConditionalOnProperty(name = "nexhome.storage.provider", havingValue = "s3")
@Slf4j
public class NexHomeS3FileStorage implements NexHomeFileStorage {

	static final String PROVIDER = "S3";

	private final String provider;
	private final String bucket;
	private final String place;
	private final boolean encrypt;
	private final S3Client client;
	private final S3Presigner presigner;

	public NexHomeS3FileStorage(NexHomeStorageProperties properties) {
		this(PROVIDER, properties.s3().bucket(), properties.s3().region(), properties.s3().accessKey(),
				properties.s3().secretKey(), properties.s3().endpoint(), true);
	}

	/**
	 * @param provider the name stored with each file
	 * @param encrypt ask for SSE-S3 on every object (AWS; R2 encrypts every object itself and does not take it)
	 */
	NexHomeS3FileStorage(String provider, String bucket, String region, String accessKey, String secretKey,
			String endpoint, boolean encrypt) {
		this.provider = provider;
		this.bucket = bucket.trim();
		this.encrypt = encrypt;
		this.place = blank(endpoint) ? region.trim() : endpoint.trim();
		Region awsRegion = Region.of(region.trim());
		AwsCredentialsProvider credentials = blank(accessKey) || blank(secretKey)
				? DefaultCredentialsProvider.builder().build()
				: StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()));
		S3ClientBuilder clientBuilder = S3Client.builder().region(awsRegion).credentialsProvider(credentials);
		S3Presigner.Builder presignerBuilder = S3Presigner.builder().region(awsRegion).credentialsProvider(credentials);
		if (!blank(endpoint)) {
			URI uri = URI.create(endpoint.trim());
			S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
			// S3-compatible stores do not all take the SDK's default CRC checksums: send them only when required
			clientBuilder.endpointOverride(uri).serviceConfiguration(pathStyle)
					.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
					.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
			presignerBuilder.endpointOverride(uri).serviceConfiguration(pathStyle);
		}
		this.client = clientBuilder.build();
		this.presigner = presignerBuilder.build();
	}

	@PostConstruct
	void start() {
		log.info("Files are kept in {} bucket {} ({})", provider, bucket, place);
	}

	@PreDestroy
	void stop() {
		client.close();
		presigner.close();
	}

	@Override
	public String providerName() {
		return provider;
	}

	@Override
	public String bucketName() {
		return bucket;
	}

	@Override
	public void put(String key, byte[] data, String contentType) {
		try {
			PutObjectRequest.Builder request = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType);
			if (encrypt) {
				request.serverSideEncryption(ServerSideEncryption.AES256);
			}
			client.putObject(request.build(), RequestBody.fromBytes(data));
		}
		catch (SdkException e) {
			log.error("Could not store {} in {} bucket {}", key, provider, bucket, e);
			throw new ServiceUnavailableException("We could not store the file right now; please try again shortly.");
		}
	}

	@Override
	public byte[] get(String key) {
		try {
			return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
					.asByteArray();
		}
		catch (NoSuchKeyException e) {
			throw new ResourceNotFoundException("The file is no longer in storage.");
		}
		catch (SdkException e) {
			log.error("Could not read {} from {} bucket {}", key, provider, bucket, e);
			throw new ServiceUnavailableException("We could not read the file right now; please try again shortly.");
		}
	}

	@Override
	public void delete(String key) {
		try {
			client.deleteObject(builder -> builder.bucket(bucket).key(key));
		}
		catch (SdkException e) {
			log.warn("Could not delete {} from {} bucket {}", key, provider, bucket, e);
		}
	}

	@Override
	public String signedUrl(String key, Duration validFor, String fileName, String contentType) {
		GetObjectRequest request = GetObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.responseContentType(contentType)
				.responseContentDisposition(NexHomeFiles.inlineDisposition(fileName))
				.build();
		return presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(validFor)
				.getObjectRequest(request)
				.build()).url().toString();
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
