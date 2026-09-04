package com.familyti.product.storage;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(

        @NotBlank
        String provider
) {

    public static final String S3 = "s3";
    public static final String MINIO = "minio";

    public boolean isS3() {
        return S3.equalsIgnoreCase(provider);
    }

    public boolean isMinio() {
        return MINIO.equalsIgnoreCase(provider);
    }
}
