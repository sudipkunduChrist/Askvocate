package com.askvocate.backend.service;

import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.CloudinaryRef;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Service for uploading document images to Cloudinary with OCR extraction.
 * 
 * <p>Images are uploaded to per-user folders with the {@code adv_ocr} add-on
 * enabled. Raw OCR results are returned to the caller for parsing but are
 * <b>never logged</b> by this service.
 */
@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Uploads a document image to Cloudinary with OCR enabled.
     *
     * @param file   the multipart image file to upload
     * @param folder Cloudinary folder path (e.g. "askvocate/documents/{userId}/AADHAAR")
     * @param label  image label ("front" or "back")
     * @return an {@link UploadResult} containing the Cloudinary reference and raw OCR data
     * @throws DocumentVerificationException if the upload fails
     */
    @SuppressWarnings("unchecked")
    public UploadResult uploadWithOcr(MultipartFile file, String folder, String label) {
        validateFile(file);

        try {
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                "folder", folder,
                "ocr", "adv_ocr",
                "resource_type", "image"
            );

            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), uploadParams);

            String publicId = (String) result.get("public_id");
            String secureUrl = (String) result.get("secure_url");

            // Log only non-sensitive metadata
            log.info("Document image uploaded successfully. publicId={}, label={}", publicId, label);

            CloudinaryRef ref = new CloudinaryRef(publicId, secureUrl, label);

            // Extract raw OCR data (may be null if OCR add-on is not enabled)
            Object ocrData = result.get("info");

            return new UploadResult(ref, ocrData);

        } catch (IOException e) {
            throw new DocumentVerificationException(
                "Failed to upload document image. Please try again.", e);
        } catch (Exception e) {
            throw new DocumentVerificationException(
                "An error occurred during image upload. Please try again.", e);
        }
    }

    /**
     * Deletes an uploaded image from Cloudinary by its public ID.
     *
     * @param publicId the Cloudinary public ID to delete
     */
    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Cloudinary asset deleted: {}", publicId);
        } catch (Exception e) {
            log.error("Failed to delete Cloudinary asset: {}", publicId, e);
        }
    }

    // ── Validation ──────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentVerificationException("Document image file is required and must not be empty.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new DocumentVerificationException(
                "Invalid file type. Only image files (JPEG, PNG, etc.) are accepted.");
        }

        // 10 MB max
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new DocumentVerificationException("File size exceeds the maximum allowed limit of 10 MB.");
        }
    }

    // ── Result Record ───────────────────────────────────────────────────

    /**
     * Holds the Cloudinary reference and raw OCR response for a single uploaded image.
     */
    public record UploadResult(CloudinaryRef cloudinaryRef, Object rawOcrData) {
    }
}
