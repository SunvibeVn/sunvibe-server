package vn.sunvibe.survey.core.infrastructure.media.store;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.time.Duration;

/**
 * Builds the three S3 clients of the media store (survey-media spec 4.12, A-25), each with its own bounded Apache pool:
 * content GET streaming (at least 32 connections, 2 s acquisition), background work (content PUT, worker, sweeper,
 * listing, deletion) and health probes. All use the endpoint override, region {@code auto} for R2, path-style access,
 * chunked encoding off (R2 answers 403 otherwise) and checksums only when required (plan Task A7 Step 1); no SSE header.
 * The SDK does not retry: the jobs retry by their own rules (A-7) and a GET refuses at once.
 */
public final class S3ClientsConfig {

    static final int BACKGROUND_CONNECTIONS = 16;
    static final int PROBE_CONNECTIONS = 2;
    static final Duration BACKGROUND_ACQUISITION = Duration.ofSeconds(10);
    static final Duration PROBE_ACQUISITION = Duration.ofSeconds(5);

    private S3ClientsConfig() {
    }

    static S3Client getClient(S3ClientSettings settings) {
        return client(settings, settings.getConnections(), settings.acquisitionTimeout());
    }

    static S3Client backgroundClient(S3ClientSettings settings) {
        return client(settings, BACKGROUND_CONNECTIONS, BACKGROUND_ACQUISITION);
    }

    static S3Client probeClient(S3ClientSettings settings) {
        return client(settings, PROBE_CONNECTIONS, PROBE_ACQUISITION);
    }

    private static S3Client client(S3ClientSettings settings, int maxConnections, Duration acquisitionTimeout) {
        return S3Client.builder()
                .endpointOverride(settings.endpoint())
                .region(Region.of(settings.region()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        settings.accessKeyId(), settings.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(maxConnections)
                        .connectionAcquisitionTimeout(acquisitionTimeout)
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(settings.socketTimeout()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryStrategy(AwsRetryStrategy.doNotRetry())
                        .build())
                .build();
    }
}
