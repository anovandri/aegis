package com.aegisflow.api.infrastructure.storage;

public class DocumentStorageException extends RuntimeException {
    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
