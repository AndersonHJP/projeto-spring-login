package com.familyti.product.storage;

import com.familyti.product.config.S3Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "storage.provider", havingValue = StorageProperties.S3)
    @EnableConfigurationProperties(S3Properties.class)
    static class S3ClientConfig {

        @Bean
        AwsCredentialsProvider awsCredentialsProvider() {
            return DefaultCredentialsProvider.create();
        }

        @Bean
        S3Client s3Client(S3Properties properties, AwsCredentialsProvider credentialsProvider) {
            return S3Client.builder()
                    .region(Region.of(properties.region()))
                    .credentialsProvider(credentialsProvider)
                    .build();
        }

        @Bean
        S3Presigner s3Presigner(S3Properties properties, AwsCredentialsProvider credentialsProvider) {
            return S3Presigner.builder()
                    .region(Region.of(properties.region()))
                    .credentialsProvider(credentialsProvider)
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "storage.provider", havingValue = StorageProperties.MINIO)
    @EnableConfigurationProperties(MinioProperties.class)
    static class MinioClientConfig {

        private static final Region MINIO_REGION = Region.US_EAST_1;

        private static final S3Configuration PATH_STYLE = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        @Bean
        AwsCredentialsProvider minioCredentialsProvider(MinioProperties properties) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        }

        @Bean
        S3Client minioClient(MinioProperties properties, AwsCredentialsProvider credentialsProvider) {
            return S3Client.builder()
                    .endpointOverride(URI.create(properties.endpoint()))
                    .region(MINIO_REGION)
                    .credentialsProvider(credentialsProvider)
                    .forcePathStyle(true)
                    .build();
        }

        @Bean
        S3Presigner minioPresigner(MinioProperties properties, AwsCredentialsProvider credentialsProvider) {
            return S3Presigner.builder()
                    .endpointOverride(URI.create(properties.endpoint()))
                    .region(MINIO_REGION)
                    .credentialsProvider(credentialsProvider)
                    .serviceConfiguration(PATH_STYLE)
                    .build();
        }
    }
}
