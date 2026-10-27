package com.askvocate.backend.service;

import com.askvocate.backend.entity.DocType;
import com.askvocate.backend.model.UserDoc;
import com.askvocate.backend.repository.UserDocRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {

    @Autowired
    private UserDocRepository userDocRepository;

    @Autowired
    private S3StorageService storageService;

    // ✅ Upload document - Saves to S3 AND MongoDB
    public UserDoc uploadDocument(String userId, DocType docType, MultipartFile file) throws Exception {
        file = DocumentImageCompressor.compressIfNeeded(file);
        // 1. Upload to S3
        String folder = "documents/" + docType.name();
        String fileUrl = storageService.uploadFile(file, folder);

        // 2. Save metadata to MongoDB
        UserDoc userDoc = userDocRepository.findByUserIdAndDocType(userId, docType)
                .orElseGet(UserDoc::new);
        userDoc.setUserId(userId);
        userDoc.setDocType(docType);
        userDoc.setFileUrl(fileUrl);
        userDoc.setStoragePath(folder);
        userDoc.setUploadedAt(Instant.now().toEpochMilli());
        userDoc.setStatus(com.askvocate.backend.entity.DocStatus.UPLOADED);

        return userDocRepository.save(userDoc);  // ✅ Saves to MongoDB
    }

    // ✅ Get signed URL - Retrieves from MongoDB, generates pre-signed URL from S3
    public String getDocumentSignedUrl(String userId, DocType docType) throws Exception {
        // 1. Get document metadata from MongoDB
        Optional<UserDoc> docOptional = userDocRepository.findByUserIdAndDocType(userId, docType);

        if (docOptional.isEmpty()) {
            throw new IllegalArgumentException("Document not found for user: " + userId + " and type: " + docType);
        }

        UserDoc doc = docOptional.get();
        String fileUrl = doc.getFileUrl();  // Get S3 URL from MongoDB

        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new IllegalStateException("Document URL is empty");
        }

        // 2. Generate pre-signed URL from S3
        String publicId = storageService.extractPublicId(fileUrl);
        String signedUrl = storageService.getSignedUrl(publicId);

        // Update MongoDB so the stored URL stays fresh and viewable
        doc.setFileUrl(signedUrl);
        userDocRepository.save(doc);

        return signedUrl;
    }

    // ✅ Get all documents for a user - From MongoDB with fresh pre-signed URLs
    public List<UserDoc> getUserDocuments(String userId) {
        List<UserDoc> docs = userDocRepository.findByUserId(userId);
        for (UserDoc doc : docs) {
            if (doc.getFileUrl() != null && !doc.getFileUrl().isEmpty()) {
                String publicId = storageService.extractPublicId(doc.getFileUrl());
                doc.setFileUrl(storageService.getSignedUrl(publicId));
            }
        }
        return docs;
    }

    // ✅ Get specific document - From MongoDB with fresh pre-signed URL
    public Optional<UserDoc> getDocument(String userId, DocType docType) {
        return userDocRepository.findByUserIdAndDocType(userId, docType)
                .map(doc -> {
                    if (doc.getFileUrl() != null && !doc.getFileUrl().isEmpty()) {
                        String publicId = storageService.extractPublicId(doc.getFileUrl());
                        doc.setFileUrl(storageService.getSignedUrl(publicId));
                    }
                    return doc;
                });
    }

    // ✅ Delete document - From S3 AND MongoDB
    public void deleteDocument(String userId, DocType docType) throws Exception {
        // 1. Get document metadata from MongoDB
        Optional<UserDoc> docOptional = userDocRepository.findByUserIdAndDocType(userId, docType);

        if (docOptional.isEmpty()) {
            throw new IllegalArgumentException("Document not found");
        }

        UserDoc doc = docOptional.get();

        // 2. Delete from S3
        if (doc.getFileUrl() != null && !doc.getFileUrl().isEmpty()) {
            try {
                String publicId = storageService.extractPublicId(doc.getFileUrl());
                storageService.deleteFile(publicId);
            } catch (Exception e) {
                System.err.println("Failed to delete from S3: " + e.getMessage());
            }
        }

        // 3. Delete from MongoDB
        userDocRepository.delete(doc);  // ✅ Deletes from MongoDB
    }
}
