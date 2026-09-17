package com.familyti.product.dto;

import com.familyti.product.model.Photo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PhotoResponse")
class PhotoResponseTest {

    private static final String PRESIGNED = "http://localhost:9000/app-photos-bucket/users/1/photos/a.png?X-Amz-Sig=x";

    private Photo photoStoredAt(String objectUrl) {
        return Photo.builder()
                .id(1L)
                .originalFilename("a.png")
                .storedFilename("a.png")
                .contentType("image/png")
                .sizeBytes(10L)
                .s3Key("users/1/photos/a.png")
                .s3Url(objectUrl)
                .build();
    }

    @Test
    @DisplayName("virtual-hosted S3 url should be reported as s3")
    void shouldDetectS3() {
        Photo photo = photoStoredAt("https://meu-bucket.s3.us-east-2.amazonaws.com/users/1/photos/a.png");

        assertThat(PhotoResponse.from(photo, PRESIGNED).provider()).isEqualTo("s3");
    }

    @Test
    @DisplayName("path-style S3 url should be reported as s3")
    void shouldDetectS3PathStyle() {
        Photo photo = photoStoredAt("https://s3.us-east-2.amazonaws.com/meu-bucket/users/1/photos/a.png");

        assertThat(PhotoResponse.from(photo, PRESIGNED).provider()).isEqualTo("s3");
    }

    @Test
    @DisplayName("MinIO url should be reported as minio, inside or outside docker")
    void shouldDetectMinio() {
        assertThat(PhotoResponse.from(photoStoredAt("http://localhost:9000/app-photos-bucket/users/1/photos/a.png"),
                PRESIGNED).provider()).isEqualTo("minio");

        assertThat(PhotoResponse.from(photoStoredAt("http://minio:9000/app-photos-bucket/users/1/photos/a.png"),
                PRESIGNED).provider()).isEqualTo("minio");
    }

    @Test
    @DisplayName("missing object url should leave the provider unknown instead of guessing")
    void shouldReturnNullWhenObjectUrlIsMissing() {
        assertThat(PhotoResponse.from(photoStoredAt(null), PRESIGNED).provider()).isNull();
        assertThat(PhotoResponse.from(photoStoredAt("  "), PRESIGNED).provider()).isNull();
    }
}
