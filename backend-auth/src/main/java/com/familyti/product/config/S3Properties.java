package com.familyti.product.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@Validated
@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(

        @NotBlank
        String bucket,

        @NotBlank
        String region,

        @Min(1)
        int presignExpirationMinutes
) {
}
