package com.familyti.product.service;

import com.familyti.product.config.S3Properties;
import com.familyti.product.exception.StorageException;
import com.familyti.product.service.impl.S3StorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3StorageServiceImpl")
class S3StorageServiceImplTest {

    private static final String BUCKET = "app-photos-bucket";
    private static final String KEY = "users/12/photos/uuid.jpg";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        storageService = new S3StorageServiceImpl(s3Client, s3Presigner, new S3Properties(BUCKET, "us-east-1", 15));
    }

    @Test
    @DisplayName("upload envia PutObject com bucket, key, content-type e tamanho, sem ACL publica")
    void shouldPutObject() {
        storageService.upload(KEY, new ByteArrayInputStream(new byte[4]), 4L, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));

        PutObjectRequest sent = request.getValue();
        assertThat(sent.bucket()).isEqualTo(BUCKET);
        assertThat(sent.key()).isEqualTo(KEY);
        assertThat(sent.contentType()).isEqualTo("image/jpeg");
        assertThat(sent.contentLength()).isEqualTo(4L);
        assertThat(sent.acl()).isNull();
    }

    @Test
    @DisplayName("presign usa a expiracao configurada e devolve a URL assinada")
    void shouldPresignWithConfiguredExpiration() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://bucket.s3.amazonaws.com/key?X-Amz-Signature=abc"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String url = storageService.generatePresignedUrl(KEY);

        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(request.capture());

        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(url).contains("X-Amz-Signature");
    }

    @Test
    @DisplayName("delete envia DeleteObjectRequest para a key correta")
    void shouldDeleteObject() {
        storageService.delete(KEY);

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());

        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("traduz S3Exception em StorageException preservando a mensagem da AWS")
    void shouldTranslateS3Exception() {
        S3Exception awsFailure = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("Access Denied").build())
                .message("Access Denied")
                .build();

        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(awsFailure);

        assertThatThrownBy(() -> storageService.delete(KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Access Denied")
                .hasMessageContaining(KEY);
    }
}
