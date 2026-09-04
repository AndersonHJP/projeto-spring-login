package com.familyti.product.storage;

import com.familyti.product.util.LoggerUtil;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;


@Component
@ConditionalOnStorageProvider(StorageProperties.MINIO)
public class MinioBucketInitializer implements ApplicationRunner {

    private final S3Client minioClient;
    private final MinioProperties properties;

    public MinioBucketInitializer(S3Client minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bucket = properties.bucket();
        try {
            minioClient.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            LoggerUtil.logInfo(getClass(), "run", "Bucket '{}' ja existe no MinIO.", bucket);
        } catch (NoSuchBucketException e) {
            create(bucket);
        }
    }

    private void create(String bucket) {
        try {
            minioClient.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            LoggerUtil.logInfo(getClass(), "create", "Bucket '{}' criado no MinIO.", bucket);
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException e) {
            LoggerUtil.logInfo(getClass(), "create", "Bucket '{}' ja havia sido criado.", bucket);
        }
    }
}