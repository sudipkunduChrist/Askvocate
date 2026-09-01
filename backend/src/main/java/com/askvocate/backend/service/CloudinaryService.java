package com.askvocate.backend.service;

import com.askvocate.backend.model.CloudinaryRef;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    @Autowired
    private Cloudinary cloudinary;

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        Map<String, Object> options = ObjectUtils.asMap("folder", folder);
        Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), options);
        return (String) uploadResult.get("secure_url");
    }

    /**
     * Uploads a file with OCR enabled and returns both the Cloudinary reference
     * and the raw OCR data extracted from the image.
     */
    public UploadResult uploadWithOcr(MultipartFile file, String folder, String tag) throws IOException {
        Map<String, Object> options = ObjectUtils.asMap(
                "folder", folder,
                "tags", tag,
                "ocr", "adv_ocr"
        );
        
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult = (Map<String, Object>) cloudinary.uploader().upload(file.getBytes(), options);
        
        String publicId = (String) uploadResult.get("public_id");
        String secureUrl = (String) uploadResult.get("secure_url");
        Object ocrData = uploadResult.get("info");
        
        CloudinaryRef ref = new CloudinaryRef();
        ref.setPublicId(publicId);
        ref.setSecureUrl(secureUrl);
        
        return new UploadResult(ref, ocrData);
    }

    // ✅ Signed URL - 1 hour (FIXED)
    public String getSignedUrl(String publicId) {
        return cloudinary.url()
                .secure(true)
                .signed(true)
                .generate(publicId);
    }

    // ✅ Extract public ID from URL
    public String extractPublicId(String fileUrl) {
        try {
            String[] parts = fileUrl.split("/");
            int uploadIndex = -1;

            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("upload")) {
                    uploadIndex = i;
                    break;
                }
            }

            if (uploadIndex == -1) {
                String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
                int lastDot = fileName.lastIndexOf(".");
                return lastDot != -1 ? fileName.substring(0, lastDot) : fileName;
            }

            StringBuilder publicId = new StringBuilder();
            for (int i = uploadIndex + 2; i < parts.length; i++) {
                if (i > uploadIndex + 2) publicId.append("/");
                publicId.append(parts[i]);
            }

            String result = publicId.toString();
            int lastDot = result.lastIndexOf(".");
            return lastDot != -1 ? result.substring(0, lastDot) : result;
        } catch (Exception e) {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            int lastDot = fileName.lastIndexOf(".");
            return lastDot != -1 ? fileName.substring(0, lastDot) : fileName;
        }
    }

    public void deleteFile(String publicId) throws IOException {
        cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
    }

    public void delete(String publicId) throws IOException {
        deleteFile(publicId);
    }

    /**
     * Result of uploading a file with OCR to Cloudinary.
     *
     * @param cloudinaryRef reference to the uploaded asset (public ID, URL, etc.)
     * @param rawOcrData    the raw OCR extraction data from Cloudinary (if available)
     */
    public record UploadResult(
            CloudinaryRef cloudinaryRef,
            Object rawOcrData
    ) {
    }
}