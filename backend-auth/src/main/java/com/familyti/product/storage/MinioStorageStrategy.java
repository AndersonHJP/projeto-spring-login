package com.familyti.product.storage;

import com.familyti.product.exception.StorageException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Component
@ConditionalOnStorageProvider(StorageProperties.MINIO)
public class MinioStorageStrategy implements StorageStrategy {

    private final S3Client minioClient;
    private final S3Presigner minioPresigner;
    private final MinioProperties properties;

    public MinioStorageStrategy(S3Client minioClient, S3Presigner minioPresigner, MinioProperties properties) {
        this.minioClient = minioClient;
        this.minioPresigner = minioPresigner;
        this.properties = properties;
    }

    @Override
    public void upload(String key, String contentType, byte[] bytes) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build();
        try {
            minioClient.putObject(request, RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw storageFailure("upload", key, e);
        } catch (Exception e) {
            throw new StorageException("Falha ao enviar o arquivo para o MinIO (key=" + key + ").", e);
        }
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();
        try {
            minioClient.deleteObject(request);
        } catch (S3Exception e) {
            throw storageFailure("delete", key, e);
        } catch (Exception e) {
            throw new StorageException("Falha ao remover o arquivo do MinIO (key=" + key + ").", e);
        }
    }

    @Override
    public String generateUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(properties.presignExpirationMinutes()))
                .getObjectRequest(getObjectRequest)
                .build();

        try {
            return minioPresigner.presignGetObject(presignRequest).url().toExternalForm();
        } catch (Exception e) {
            throw new StorageException("Falha ao gerar a URL pré-assinada (key=" + key + ").", e);
        }
    }

    @Override
    public String objectUrl(String key) {
        return minioClient.utilities()
                .getUrl(GetUrlRequest.builder().bucket(properties.bucket()).key(key).build())
                .toExternalForm();
    }

    private StorageException storageFailure(String operation, String key, S3Exception e) {
        String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
        return new StorageException(
                "Erro do MinIO na operação '" + operation + "' (key=" + key + "): " + detail, e);
    }
}