package com.aegisflow.api.infrastructure.storage;

import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.ports.DocumentStoragePort;
import com.aegisflow.api.ports.StoredDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "aegisflow.storage", name = "provider", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryDocumentStorageAdapter implements DocumentStoragePort {
    private final StorageProperties properties;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    public InMemoryDocumentStorageAdapter(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredDocument store(String objectKey, SubmittedDocument document) {
        objects.put(objectKey, document.content());
        return new StoredDocument(properties.bucket(), objectKey, null);
    }

    @Override
    public byte[] retrieve(StoredDocument document) {
        byte[] content = objects.get(document.objectKey());
        if (content == null) {
            throw new DocumentStorageException("Document object not found: " + document.objectKey(), null);
        }
        return content;
    }
}
