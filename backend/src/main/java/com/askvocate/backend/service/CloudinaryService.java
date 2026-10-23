package com.askvocate.backend.service;

import com.askvocate.backend.model.CloudinaryRef;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Legacy compatibility facade.
 *
 * <p>All file storage is now backed by AWS S3 via {@link S3StorageService}.
 * This class keeps the original {@code CloudinaryService} name so that
 * every existing caller ({@link DocumentService}, {@link DocumentVerificationService},
 * {@link SelfieVerificationService}, {@link MyDocumentsController}, and all tests)
 * can continue to compile without renaming injection points.
 *
 * <p>Every method delegates directly to {@link S3StorageService}.
 * The {@code UploadResult} record is re-exported for binary compatibility.
 */
@Service
public class CloudinaryService {

    private final S3StorageService s3;

    public CloudinaryService(S3StorageService s3) {
        this.s3 = s3;
    }

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        return s3.uploadFile(file, folder);
    }

    public CloudinaryRef uploadSelfie(MultipartFile file, String folder) throws IOException {
        return s3.uploadSelfie(file, folder);
    }

    public CloudinaryRef uploadBytes(byte[] bytes, String folder, String tag) throws IOException {
        return s3.uploadBytes(bytes, folder, tag);
    }

    public byte[] downloadReference(CloudinaryRef ref) throws IOException {
        return s3.downloadReference(ref);
    }

    public UploadResult uploadWithOcr(MultipartFile file, String folder, String tag) throws IOException {
        S3StorageService.UploadResult result = s3.uploadWithOcr(file, folder, tag);
        return new UploadResult(result.cloudinaryRef(), result.rawOcrData());
    }

    public String getSignedUrl(String publicId) {
        return s3.getSignedUrl(publicId);
    }

    public String extractPublicId(String fileUrl) {
        return s3.extractPublicId(fileUrl);
    }

    public void deleteFile(String publicId) throws IOException {
        s3.deleteFile(publicId);
    }

    public void delete(String publicId) throws IOException {
        s3.delete(publicId);
    }

    /**
     * Re-exported result record — callers that reference
     * {@code CloudinaryService.UploadResult} continue to compile.
     *
     * @param cloudinaryRef reference to the uploaded asset (S3 key / public URL)
     * @param rawOcrData    raw OCR extraction data from the local Python worker
     */
    public record UploadResult(
            CloudinaryRef cloudinaryRef,
            Object rawOcrData
    ) {}
}
