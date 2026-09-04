package com.familyti.product.storage;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

@Validated
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(

        @NotBlank
        String provider
) {

    public static final String S3 = "s3";
    public static final String MINIO = "minio";

    static final String PROPERTY = "storage.provider";

    private static final List<String> VALID = List.of(S3, MINIO);

    public StorageProperties {
        provider = normalize(provider);
    }

    static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isValid(String normalizedProvider) {
        return VALID.contains(normalizedProvider);
    }

    static String validValues() {
        return String.join(", ", VALID);
    }

    public boolean isS3() {
        return S3.equals(provider);
    }

    public boolean isMinio() {
        return MINIO.equals(provider);
    }
}
