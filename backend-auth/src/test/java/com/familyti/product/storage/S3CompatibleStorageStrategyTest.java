package com.familyti.product.storage;

import com.familyti.product.exception.StorageConfigurationException;
import com.familyti.product.exception.StorageException;
import com.familyti.product.storage.StorageProperties.Backend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3CompatibleStorageStrategy")
class S3CompatibleStorageStrategyTest {

    private static final String ENDPOINT = "http://localhost:9000";
    private static final String BUCKET = "app-photos-bucket";
    private static final String KEY = "users/12/photos/uuid.jpg";

    private static final Backend AWS = new Backend(
            null, "us-east-2", BUCKET, null, null, 15, false, "Amazon S3", "AWS_S3");

    private static final Backend MINIO = new Backend(
            ENDPOINT, "us-east-1", BUCKET, "admin", "admin123", 15, true, "MinIO", "MINIO");

    @Mock
    private S3Client client;

    @Mock
    private S3Presigner presigner;

    private S3CompatibleStorageStrategy s3;
    private S3CompatibleStorageStrategy minio;

    @BeforeEach
    void setUp() {
        s3 = new S3CompatibleStorageStrategy("s3", AWS, client, presigner);
        minio = new S3CompatibleStorageStrategy("minio", MINIO, client, presigner);
    }

    @Test
    @DisplayName("provider() devolve o nome do backend, que e o gravado em cada foto")
    void shouldExposeProviderName() {
        assertThat(s3.provider()).isEqualTo("s3");
        assertThat(minio.provider()).isEqualTo("minio");
    }

    // --- operacoes -----------------------------------------------------------

    @Test
    @DisplayName("upload envia PutObject com bucket, key, content-type e tamanho, sem ACL publica")
    void shouldPutObject() {
        s3.upload(KEY, "image/jpeg", new byte[4]);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));

        PutObjectRequest sent = request.getValue();
        assertThat(sent.bucket()).isEqualTo(BUCKET);
        assertThat(sent.key()).isEqualTo(KEY);
        assertThat(sent.contentType()).isEqualTo("image/jpeg");
        assertThat(sent.contentLength()).isEqualTo(4L);
        assertThat(sent.acl()).isNull();
    }

    @Test
    @DisplayName("delete envia DeleteObjectRequest para a key correta")
    void shouldDeleteObject() {
        minio.delete(KEY);

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());

        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("generateUrl usa a expiracao configurada e devolve a URL assinada")
    void shouldPresignWithConfiguredExpiration() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://bucket.s3.amazonaws.com/key?X-Amz-Signature=abc"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String url = s3.generateUrl(KEY);

        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());

        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(url).contains("X-Amz-Signature");
    }

    @Test
    @DisplayName("objectUrl devolve a URL canonica do objeto")
    void shouldBuildObjectUrl() throws Exception {
        S3Utilities utilities = mock(S3Utilities.class);
        when(client.utilities()).thenReturn(utilities);
        when(utilities.getUrl(any(GetUrlRequest.class))).thenReturn(new URL(ENDPOINT + "/" + BUCKET + "/" + KEY));

        assertThat(minio.objectUrl(KEY)).isEqualTo(ENDPOINT + "/" + BUCKET + "/" + KEY);
    }

    // --- erros ---------------------------------------------------------------

    @Test
    @DisplayName("traduz S3Exception em StorageException citando o backend pelo nome de exibicao")
    void shouldTranslateS3Exception() {
        S3Exception failure = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("Access Denied").build())
                .message("Access Denied")
                .build();

        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> minio.delete(KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("MinIO")
                .hasMessageContaining("Access Denied")
                .hasMessageContaining(KEY);
    }

    @Test
    @DisplayName("falha inesperada no upload vira StorageException")
    void shouldWrapUnexpectedUploadFailure() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("conexao interrompida"));

        assertThatThrownBy(() -> s3.upload(KEY, "image/jpeg", new byte[2]))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Amazon S3")
                .hasMessageContaining(KEY);
    }

    @Test
    @DisplayName("falha inesperada no delete vira StorageException")
    void shouldWrapUnexpectedDeleteFailure() {
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("conexao interrompida"));

        assertThatThrownBy(() -> s3.delete(KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining(KEY);
    }

    @Test
    @DisplayName("falha ao assinar a URL vira StorageException")
    void shouldWrapPresignFailure() {
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("assinatura falhou"));

        assertThatThrownBy(() -> minio.generateUrl(KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining(KEY);
    }

    // --- preparo do bucket no startup ---------------------------------------

    @Test
    @DisplayName("backend sem create-bucket-if-missing nao toca no bucket")
    void shouldNotTouchBucketWhenNotRequested() {
        s3.ensureBucketExists();

        verify(client, never()).headBucket(any(HeadBucketRequest.class));
        verify(client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("nao cria nada quando o bucket ja existe")
    void shouldSkipWhenBucketExists() {
        when(client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());

        minio.ensureBucketExists();

        verify(client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("cria o bucket quando ele nao existe")
    void shouldCreateWhenBucketIsMissing() {
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("nao existe").build());

        minio.ensureBucketExists();

        ArgumentCaptor<CreateBucketRequest> request = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(client).createBucket(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
    }

    @Test
    @DisplayName("tolera outra instancia ter criado o bucket entre o head e o create")
    void shouldTolerateConcurrentCreation() {
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("nao existe").build());
        when(client.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(BucketAlreadyOwnedByYouException.builder().message("ja existe").build());

        assertThatCode(() -> minio.ensureBucketExists()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cria o bucket quando o head devolve 404 sem tipar a excecao")
    void shouldCreateWhenHeadReturnsPlain404() {
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        minio.ensureBucketExists();

        verify(client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("403 vira mensagem citando as variaveis de chave e segredo do backend")
    void shouldExplainForbidden() {
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("Forbidden").build());

        assertThatThrownBy(() -> minio.ensureBucketExists())
                .isInstanceOf(StorageConfigurationException.class)
                .hasMessageContaining("recusou as credenciais (403)")
                .hasMessageContaining("MINIO_ACCESS_KEY")
                .hasMessageContaining("MINIO_SECRET_KEY");
        verify(client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("outro erro do backend cita o status e o endpoint")
    void shouldExplainOtherS3Error() {
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder()
                        .statusCode(500)
                        .awsErrorDetails(AwsErrorDetails.builder().errorMessage("boom").build())
                        .message("Internal Error")
                        .build());

        assertThatThrownBy(() -> minio.ensureBucketExists())
                .isInstanceOf(StorageConfigurationException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageContaining(ENDPOINT)
                .hasMessageContaining("boom");
    }

    @Test
    @DisplayName("container fora do ar vira mensagem sobre o endpoint e o docker compose")
    void shouldExplainUnreachableBackend() {
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(SdkClientException.builder().message("connection refused").build());

        assertThatThrownBy(() -> minio.ensureBucketExists())
                .isInstanceOf(StorageConfigurationException.class)
                .hasMessageContaining("Nao foi possivel conectar ao MinIO")
                .hasMessageContaining("docker compose ps")
                .hasMessageContaining("MINIO_ENDPOINT");
    }
}
