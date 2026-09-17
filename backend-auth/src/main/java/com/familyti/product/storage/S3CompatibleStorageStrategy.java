package com.familyti.product.storage;

import com.familyti.product.exception.StorageConfigurationException;
import com.familyti.product.exception.StorageException;
import com.familyti.product.storage.StorageProperties.Backend;
import com.familyti.product.util.LoggerUtil;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

public class S3CompatibleStorageStrategy implements StorageStrategy {

    private static final int NOT_FOUND = 404;
    private static final int FORBIDDEN = 403;

    private final String provider;
    private final String label;
    private final String location;
    private final String envPrefix;

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final int presignExpirationMinutes;
    private final boolean createBucketIfMissing;

    public S3CompatibleStorageStrategy(String provider, Backend backend, S3Client client, S3Presigner presigner) {
        this.provider = provider;
        this.label = backend.label(provider);
        this.location = backend.isAws() ? "Amazon S3 (" + backend.region() + ")" : backend.endpoint();
        this.envPrefix = backend.envPrefix(provider);
        this.client = client;
        this.presigner = presigner;
        this.bucket = backend.bucket();
        this.presignExpirationMinutes = backend.presignExpirationMinutes();
        this.createBucketIfMissing = backend.createBucketIfMissing();
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public void upload(String key, String contentType, byte[] bytes) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build();
        try {
            client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw storageFailure("upload", key, e);
        } catch (Exception e) {
            throw new StorageException("Falha ao enviar o arquivo para o " + label + " (key=" + key + ").", e);
        }
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        try {
            client.deleteObject(request);
        } catch (S3Exception e) {
            throw storageFailure("delete", key, e);
        } catch (Exception e) {
            throw new StorageException("Falha ao remover o arquivo do " + label + " (key=" + key + ").", e);
        }
    }

    @Override
    public String generateUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignExpirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        try {
            return presigner.presignGetObject(presignRequest).url().toExternalForm();
        } catch (Exception e) {
            throw new StorageException("Falha ao gerar a URL pre-assinada (key=" + key + "): "
                    + rootCauseOf(e), e);
        }
    }

    @Override
    public String objectUrl(String key) {
        return client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucket).key(key).build())
                .toExternalForm();
    }

    // --- preparo do bucket no startup ---------------------------------------

    @Override
    public void ensureBucketExists() {
        if (!createBucketIfMissing) {
            return;
        }
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            LoggerUtil.logInfo(getClass(), "ensureBucketExists", "Bucket '{}' ja existe no {}.", bucket, label);
        } catch (NoSuchBucketException e) {
            create();
        } catch (S3Exception e) {
            if (e.statusCode() == NOT_FOUND) {
                create();
                return;
            }
            throw translate(e);
        } catch (SdkClientException e) {
            throw unreachable(e);
        }
    }

    private void create() {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            LoggerUtil.logInfo(getClass(), "create", "Bucket '{}' criado no {}.", bucket, label);
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException e) {
            LoggerUtil.logInfo(getClass(), "create", "Bucket '{}' ja havia sido criado.", bucket);
        } catch (S3Exception e) {
            throw translate(e);
        } catch (SdkClientException e) {
            throw unreachable(e);
        }
    }

    private StorageConfigurationException translate(S3Exception e) {
        if (e.statusCode() == FORBIDDEN) {
            return new StorageConfigurationException(
                    "O " + label + " em '" + location + "' recusou as credenciais (403) ao acessar o bucket '"
                            + bucket + "'. " + envPrefix + "_ACCESS_KEY e " + envPrefix + "_SECRET_KEY precisam ser "
                            + "iguais as credenciais com que o servico subiu. Atencao: rodando fora do Docker, o "
                            + "Spring nao le o arquivo .env - as variaveis precisam estar no ambiente do processo.", e);
        }
        String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
        return new StorageConfigurationException(
                "Falha ao preparar o bucket '" + bucket + "' no " + label + " em '" + location
                        + "' (HTTP " + e.statusCode() + "): " + detail, e);
    }

    private StorageConfigurationException unreachable(SdkClientException e) {
        return new StorageConfigurationException(
                "Nao foi possivel conectar ao " + label + " em '" + location + "' para preparar o bucket '"
                        + bucket + "'. Confira se o container esta no ar ('docker compose ps') e se "
                        + envPrefix + "_ENDPOINT aponta para ele: o host publicado fora do Docker, o nome do "
                        + "servico dentro do Compose.", e);
    }

    // --- erros de operacao ---------------------------------------------------

    private StorageException storageFailure(String operation, String key, S3Exception e) {
        String detail = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
        return new StorageException(
                "Erro do " + label + " na operacao '" + operation + "' (key=" + key + "): " + detail, e);
    }

    /** Mensagem da causa mais funda, para o erro dizer o que realmente aconteceu. */
    private static String rootCauseOf(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
