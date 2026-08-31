package com.familyti.product.service;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;


public interface S3StorageService {

    void upload(String key, InputStream content, long sizeBytes, String contentType);
    ResponseInputStream<GetObjectResponse> download(String key);
    String generatePresignedUrl(String key);
    void delete(String key);
    String objectUrl(String key);
}
