package com.familyti.product.storage;


public interface StorageStrategy {

    void upload(String key, String contentType, byte[] bytes);
    void delete(String key);
    String generateUrl(String key);
    String objectUrl(String key);
}
