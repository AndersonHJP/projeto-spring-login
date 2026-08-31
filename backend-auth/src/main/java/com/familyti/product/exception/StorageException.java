package com.familyti.product.exception;

public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public static StorageException of(String operation, String key, String detail, Throwable cause) {
        return new StorageException(
                "Erro do S3 na operação '" + operation + "' (key=" + key + "): " + detail, cause);
    }
}