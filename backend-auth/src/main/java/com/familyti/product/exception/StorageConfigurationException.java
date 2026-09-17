package com.familyti.product.exception;

public class StorageConfigurationException extends IllegalStateException {

    public StorageConfigurationException(String message) {
        super(message);
    }

    public StorageConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
