package dev.fajar.hris.storage.data.di

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.retries.StandardRetryStrategy
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration

fun createObjectStorageClient(settings: ObjectStorageSettings): S3Client =
    S3Client.builder()
        .endpointOverride(settings.endpoint)
        .region(Region.of(settings.region))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(settings.accessKey, settings.secretKey)
            )
        )
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build()
        )
        .httpClientBuilder(
            UrlConnectionHttpClient.builder()
                .connectionTimeout(settings.connectTimeout)
                .socketTimeout(settings.readTimeout)
        )
        .overrideConfiguration(
            ClientOverrideConfiguration.builder()
                .apiCallTimeout(settings.callTimeout)
                .apiCallAttemptTimeout(settings.callTimeout)
                .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())
                .build()
        )
        .build()
