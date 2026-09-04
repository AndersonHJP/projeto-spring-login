package com.familyti.product.storage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@Validated
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(

        @NotBlank
        String endpoint,

        @NotBlank
        String bucket,

        @NotBlank
        String accessKey,

        @NotBlank
        String secretKey,

        @Min(1)
        int presignExpirationMinutes
) {
}
