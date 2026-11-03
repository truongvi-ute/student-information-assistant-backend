package vn.hcmute.edu.sia.service.implement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import vn.hcmute.edu.sia.dto.internal.StoredDocumentFile;
import vn.hcmute.edu.sia.exception.DocumentStorageException;
import vn.hcmute.edu.sia.service.DocumentStorageService;
import vn.hcmute.edu.sia.validation.DocumentUploadValidator;

@Service
@RequiredArgsConstructor
public class RustFsDocumentStorageService implements DocumentStorageService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RustFsDocumentStorageService.class);

    private final S3Client s3Client;
    private final DocumentUploadValidator documentUploadValidator;

    @Value("${app.storage.rustfs.bucket}")
    private String bucketName;

    private volatile boolean bucketReady;

    @Override
    public StoredDocumentFile store(MultipartFile file, String mimeType) throws IOException {
        ensureBucketExists();

        String originalFilename = documentUploadValidator.getSafeFilename(
                file.getOriginalFilename()
        );
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.'))
                .toLowerCase(Locale.ROOT);
        String storagePath = "documents/" + UUID.randomUUID() + extension;
        long sizeBytes = file.getSize();
        Path temporaryFile = Files.createTempFile("sia-document-upload-", extension);

        try {
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(
                        inputStream,
                        temporaryFile,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            String checksumSha256 = calculateSha256(temporaryFile);
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .contentType(mimeType)
                    .metadata(Map.of("sha256", checksumSha256))
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromFile(temporaryFile));

            return new StoredDocumentFile(
                    storagePath,
                    originalFilename,
                    mimeType,
                    sizeBytes,
                    checksumSha256
            );
        } catch (IOException exception) {
            deleteObjectAfterFailedUpload(storagePath, exception);
            throw exception;
        } catch (SdkException exception) {
            deleteObjectAfterFailedUpload(storagePath, exception);
            throw new DocumentStorageException(
                    "RustFS could not store the uploaded document",
                    exception
            );
        } finally {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException cleanupException) {
                LOGGER.warn("Could not delete temporary document upload file", cleanupException);
            }
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .build());
        } catch (SdkException exception) {
            throw new DocumentStorageException(
                    "RustFS could not delete the uploaded document",
                    exception
            );
        }
    }

    private synchronized void ensureBucketExists() {
        if (bucketReady) {
            return;
        }

        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            bucketReady = true;
            return;
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw new DocumentStorageException(
                        "RustFS bucket could not be checked",
                        exception
                );
            }
        } catch (SdkException exception) {
            throw new DocumentStorageException(
                    "RustFS is unavailable",
                    exception
            );
        }

        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 409) {
                throw new DocumentStorageException(
                        "RustFS bucket could not be created",
                        exception
                );
            }
        } catch (SdkException exception) {
            throw new DocumentStorageException(
                    "RustFS bucket could not be created",
                    exception
            );
        }

        bucketReady = true;
    }

    private String calculateSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream inputStream = Files.newInputStream(file)) {
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void deleteObjectAfterFailedUpload(String storagePath, Exception originalException) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .build());
        } catch (SdkException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}
