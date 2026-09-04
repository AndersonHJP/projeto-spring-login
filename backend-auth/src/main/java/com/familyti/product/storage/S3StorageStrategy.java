package com.familyti.product.storage;

import com.familyti.product.config.S3Properties;
import com.familyti.product.exception.StorageException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
public class S3StorageStrategy implements StorageStrategy {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3StorageStrategy(S3Client s3Client, S3Presigner s3Presigner, S3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
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
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw storageFailure("upload", key, e);
        } catch (Exception e) {
            throw new StorageException("Falha ao enviar o arquivo para o S3 (key=" + key + ").", e);
        }
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();
        try {
            s3Client.deleteObject(request);
        } catch (S3Exception e) {
            throw storageFailure("delete", key, e);
        } catch (Exception e) {
            throw new StorageException("Falha ao remover o arquivo do S3 (key=" + key + ").", e);
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
            return s3Presigner.presignGetObject(presignRequest).url().toExternalForm();
        } catch (Exception e) {
            throw new StorageException("Falha ao gerar a URL pré-assinada (key=" + key + ").", e);
        }
    }

    @Override
    public String objectUrl(String key) {
        return s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(properties.bucket()).key(key).build())
                .toExternalForm();
    }

    private StorageException storageFailure(String operation, String key, S3Exception e) {
        String awsMessage = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
        return StorageException.of(operation, key, awsMessage, e);
    }
}
