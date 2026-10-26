package com.askvocate.backend.service;

import com.askvocate.backend.model.CloudinaryRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * AWS S3-backed file storage service.
 *
 * <p>Drop-in replacement for the previous Cloudinary-based service.
 * All methods keep identical signatures so callers require no changes.
 *
 * <p>S3 key layout mirrors the old Cloudinary folder/public-id convention:
 * {@code <folder>/<uuid>.<ext>}.  The "publicId" stored in {@link CloudinaryRef}
 * is therefore the full S3 object key and the "secureUrl" is the pre-signed URL
 * (regenerated on demand) or the public HTTPS URL when the bucket is public.
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final VisionVerificationClient visionVerificationClient;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    /** Pre-signed URL validity duration in days (max 7 days per AWS SigV4). */
    @Value("${aws.s3.presign-duration-days:7}")
    private int presignDurationDays;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner,
                            VisionVerificationClient visionVerificationClient) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.visionVerificationClient = visionVerificationClient;
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Determines a content-type string from the file's declared MIME type,
     * falling back to {@code application/octet-stream}.
     */
    private String contentType(MultipartFile file) {
        String ct = file.getContentType();
        return (ct != null && !ct.isBlank()) ? ct : "application/octet-stream";
    }

    /**
     * Derives a file extension from the content-type or original filename.
     */
    private String extension(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null) {
            return switch (ct) {
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> ".jpg";
            };
        }
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) return name.substring(name.lastIndexOf('.'));
        return ".jpg";
    }

    /**
     * Builds an S3 object key from a folder path and an extension.
     * E.g. {@code documents/AADHAAR/front-<uuid>.jpg}
     */
    private String buildKey(String folder, String tag, String ext) {
        return folder + "/" + tag + "-" + UUID.randomUUID() + ext;
    }

    /**
     * Uploads raw bytes to S3 and returns the object key.
     */
    private String putObject(byte[] bytes, String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        log.debug("Uploaded S3 object: key={}, bytes={}", key, bytes.length);
        return key;
    }

    /**
     * Generates an HTTPS public URL for an object in a public bucket.
     * If the bucket is private, use {@link #getSignedUrl(String)} instead.
     */
    private String publicUrl(String key) {
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }

    // ── Public API (mirrors old CloudinaryService) ────────────────────

    /**
     * Uploads a document image to S3 and returns a pre-signed URL (up to 7 days).
     * Used by {@link DocumentService} for generic uploads so the link stored in MongoDB is directly viewable.
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        String key = buildKey(folder, "file", extension(file));
        putObject(file.getBytes(), key, contentType(file));
        return getSignedUrl(key);
    }

    /**
     * Uploads a selfie image and returns a {@link CloudinaryRef} carrying the
     * S3 key as {@code publicId} and the pre-signed URL as {@code secureUrl}.
     */
    public CloudinaryRef uploadSelfie(MultipartFile file, String folder) throws IOException {
        String key = buildKey(folder, "selfie", extension(file));
        putObject(file.getBytes(), key, contentType(file));
        return new CloudinaryRef(key, getSignedUrl(key), "selfie");
    }

    /**
     * Uploads arbitrary bytes (e.g. a QR photo extracted from a PAN card)
     * and returns a {@link CloudinaryRef} with the pre-signed URL.
     *
     * @param bytes   raw image bytes
     * @param folder  S3 folder prefix
     * @param tag     label stored in {@link CloudinaryRef#getLabel()}
     */
    public CloudinaryRef uploadBytes(byte[] bytes, String folder, String tag) throws IOException {
        String key = buildKey(folder, tag, ".jpg");
        putObject(bytes, key, "image/jpeg");
        return new CloudinaryRef(key, getSignedUrl(key), tag);
    }

    /**
     * Downloads an image from S3 using the object key stored in the
     * {@link CloudinaryRef#getPublicId()} field.
     *
     * <p>Enforces a 30 MB safety cap identical to the previous Cloudinary implementation.
     */
    public byte[] downloadReference(CloudinaryRef ref) throws IOException {
        String key = ref.getPublicId();
        if (key == null || key.isBlank()) {
            throw new IOException("Invalid S3 object key in storage reference.");
        }
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            byte[] body = response.asByteArray();
            if (body.length > 30 * 1024 * 1024) {
                throw new IOException("Stored document image exceeds the 30 MB delivery limit.");
            }
            return body;
        } catch (NoSuchKeyException e) {
            throw new IOException("Stored document image is unavailable (not found in S3).", e);
        } catch (S3Exception e) {
            throw new IOException("Failed to download document image from S3: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    /**
     * Runs free local OCR before uploading the file to S3.
     * Return type mirrors the previous {@code CloudinaryService.UploadResult} record.
     */
    public UploadResult uploadWithOcr(MultipartFile file, String folder, String tag) throws IOException {
        byte[] image = file.getBytes();
        boolean qrOnly = folder.endsWith("/AADHAAR") || folder.endsWith("/PAN");
        Map<String, Object> ocrData = qrOnly ? Map.of() : visionVerificationClient.localOcr(
                image, folder.endsWith("/AADHAAR") ? tag : folder.endsWith("/PAN") ? "pan" : "");

        String ext = extension(file);
        String key = buildKey(folder, tag, ext);
        putObject(image, key, contentType(file));

        CloudinaryRef ref = new CloudinaryRef();
        ref.setPublicId(key);
        ref.setSecureUrl(getSignedUrl(key));
        ref.setLabel(tag);

        return new UploadResult(ref, ocrData);
    }

    /**
     * Generates a pre-signed GET URL valid for up to 7 days (AWS SigV4 maximum).
     *
     * @param publicId the S3 object key or full S3 URL
     */
    public String getSignedUrl(String publicId) {
        String key = extractPublicId(publicId);
        int days = Math.min(Math.max(presignDurationDays, 1), 7);
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofDays(days))
                .getObjectRequest(r -> r.bucket(bucketName).key(key))
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Extracts the S3 object key from either a full S3 HTTPS URL or the key itself.
     *
     * <p>Handles:
     * <ul>
     *   <li>Full URL: {@code https://bucket.s3.region.amazonaws.com/folder/key.jpg}</li>
     *   <li>Path-style URL: {@code https://s3.region.amazonaws.com/bucket/folder/key.jpg}</li>
     *   <li>Raw key: {@code folder/key.jpg}</li>
     * </ul>
     */
    public String extractPublicId(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return fileUrl;
        try {
            // Virtual-hosted style: https://<bucket>.s3.<region>.amazonaws.com/<key>
            String marker = ".amazonaws.com/";
            int idx = fileUrl.indexOf(marker);
            if (idx != -1) {
                String afterHost = fileUrl.substring(idx + marker.length());
                // Strip pre-sign query string if present
                int q = afterHost.indexOf('?');
                return q != -1 ? afterHost.substring(0, q) : afterHost;
            }
            // If it doesn't look like an S3 URL, return as-is (it may already be a key)
            return fileUrl;
        } catch (Exception e) {
            return fileUrl;
        }
    }

    /**
     * Deletes an object from S3 by its key (stored as {@code publicId}).
     * Silently ignores NoSuchKey errors.
     */
    public void deleteFile(String publicId) throws IOException {
        if (publicId == null || publicId.isBlank()) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(publicId)
                    .build());
            log.debug("Deleted S3 object: key={}", publicId);
        } catch (NoSuchKeyException ignored) {
            // Already gone — treat as success
        } catch (S3Exception e) {
            throw new IOException("Failed to delete S3 object '" + publicId + "': "
                    + e.awsErrorDetails().errorMessage(), e);
        }
    }

    /** Alias kept for callers that use {@code delete(publicId)}. */
    public void delete(String publicId) throws IOException {
        deleteFile(publicId);
    }

    /**
     * Result of uploading a file with OCR to S3.
     *
     * @param cloudinaryRef reference to the uploaded asset (S3 key as publicId, public URL as secureUrl)
     * @param rawOcrData    the raw OCR extraction data (if available)
     */
    public record UploadResult(
            CloudinaryRef cloudinaryRef,
            Object rawOcrData
    ) {}
}
