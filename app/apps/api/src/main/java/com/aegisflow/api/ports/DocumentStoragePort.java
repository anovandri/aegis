package com.aegisflow.api.ports;

import com.aegisflow.api.application.SubmittedDocument;

public interface DocumentStoragePort {
    StoredDocument store(String objectKey, SubmittedDocument document);

    byte[] retrieve(StoredDocument document);
}
