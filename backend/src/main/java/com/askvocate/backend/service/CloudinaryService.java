package com.askvocate.backend.service;

import com.askvocate.backend.model.CloudinaryRef;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class CloudinaryService {

    @Autowired
    private Cloudinary cloudinary;

    @Autowired
    private VisionVerificationClient visionVerificationClient;

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        Map<String, Object> options = ObjectUtils.asMap("folder", folder);
        Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), options);
        return (String) uploadResult.get("secure_url");
    }

    public CloudinaryRef uploadSelfie(MultipartFile file, String folder) throws IOException {
        Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap("folder", folder));
        return new CloudinaryRef((String) result.get("public_id"), (String) result.get("secure_url"), "selfie");
    }

    public byte[] downloadReference(CloudinaryRef ref) throws IOException {
        try {
            URI uri = URI.create(ref.getSecureUrl());
            if (!"https".equals(uri.getScheme()) || !"res.cloudinary.com".equals(uri.getHost())) {
                throw new IOException("Invalid Cloudinary image URL.");
            }
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > 10 * 1024 * 1024) {
                throw new IOException("Aadhaar back image is unavailable or too large.");
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Aadhaar image download interrupted.", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid Cloudinary image URL.", e);
        }
    }

    /**
     * Runs free local OCR before uploading the file to Cloudinary storage.
     */
    public UploadResult uploadWithOcr(MultipartFile file, String folder, String tag) throws IOException {
        byte[] image = file.getBytes();
        Map<String, Object> ocrData = visionVerificationClient.localOcr(
                image, folder.endsWith("/AADHAAR") ? tag : "");
        Map<String, Object> options = ObjectUtils.asMap(
                "folder", folder,
                "tags", tag
        );
        
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult = (Map<String, Object>) cloudinary.uploader().upload(image, options);
        
        String publicId = (String) uploadResult.get("public_id");
        String secureUrl = (String) uploadResult.get("secure_url");
        
        CloudinaryRef ref = new CloudinaryRef();
        ref.setPublicId(publicId);
        ref.setSecureUrl(secureUrl);
        ref.setLabel(tag);
        
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
