package com.familyti.product.dto;

import com.familyti.product.model.Photo;

import java.time.OffsetDateTime;
import java.time.ZoneId;


public record PhotoResponse(
        Long id,
        String originalFilename,
        String storedFilename,
        String contentType,
        Long sizeBytes,
        String s3Key,
        String url,
        String title,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static PhotoResponse from(Photo photo, String presignedUrl) {
        return new PhotoResponse(
                photo.getId(),
                photo.getOriginalFilename(),
                photo.getStoredFilename(),
                photo.getContentType(),
                photo.getSizeBytes(),
                photo.getS3Key(),
                presignedUrl,
                photo.getTitle(),
                photo.getDescription(),
                toOffset(photo.getCreatedAt()),
                toOffset(photo.getUpdatedAt())
        );
    }

    private static OffsetDateTime toOffset(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}