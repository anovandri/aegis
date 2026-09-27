package com.aegisflow.api.infrastructure.storage;

import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.ports.DocumentStoragePort;
import com.aegisflow.api.ports.StoredDocument;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketVersioningArgs;
import io.minio.errors.MinioException;
import io.minio.messages.VersioningConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
@ConditionalOnProperty(prefix = "aegisflow.storage", name = "provider", havingValue = "minio")
public class MinioDocumentStorageAdapter implements DocumentStoragePort {
    private final MinioClient minioClient;
    private final StorageProperties properties;

    public MinioDocumentStorageAdapter(MinioClient minioClient, StorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public StoredDocument store(String objectKey, SubmittedDocument document) {
        try {
            ensureBucketExists();
            var response = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(document.content()), document.sizeBytes(), -1)
                            .contentType(document.mediaType())
                            .build()
            );
            return new StoredDocument(properties.bucket(), objectKey, response.versionId());
        } catch (MinioException exception) {
            throw new DocumentStorageException("MinIO rejected document storage request", exception);
        } catch (Exception exception) {
            throw new DocumentStorageException("Document storage failed", exception);
        }
    }

    @Override
    public byte[] retrieve(StoredDocument document) {
        try (var response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(document.bucket())
                        .object(document.objectKey())
                        .versionId(document.storageVersionId())
                        .build()
        )) {
            return response.readAllBytes();
        } catch (MinioException exception) {
            throw new DocumentStorageException("MinIO rejected document retrieval request", exception);
        } catch (Exception exception) {
            throw new DocumentStorageException("Document retrieval failed", exception);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(properties.bucket())
                        .build()
        );
        if (!bucketExists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(properties.bucket())
                            .build()
            );
        }
        minioClient.setBucketVersioning(
                SetBucketVersioningArgs.builder()
                        .bucket(properties.bucket())
                        .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, false))
                        .build()
        );
    }
}
