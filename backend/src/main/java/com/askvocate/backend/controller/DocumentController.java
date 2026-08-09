package com.askvocate.backend.controller;

import com.askvocate.backend.entity.DocType;
import com.askvocate.backend.model.UserDoc;
import com.askvocate.backend.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    // ✅ Upload document
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam String userId,
            @RequestParam DocType docType,
            @RequestParam("file") MultipartFile file) {
        try {
            UserDoc doc = documentService.uploadDocument(userId, docType, file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Document uploaded successfully",
                    "document", doc  // Returns MongoDB document with Cloudinary URL
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Upload failed: " + e.getMessage()
                    ));
        }
    }

    // ✅ Get signed URL (1 hour)
    @GetMapping("/{userId}/{docType}/signed-url")
    public ResponseEntity<?> getSignedUrl(
            @PathVariable String userId,
            @PathVariable DocType docType) {
        try {
            String signedUrl = documentService.getDocumentSignedUrl(userId, docType);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "signedUrl", signedUrl,
                    "userId", userId,
                    "docType", docType,
                    "expiresIn", "3600 seconds (1 hour)"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Failed to generate URL: " + e.getMessage()
                    ));
        }
    }

    // ✅ Get all documents for a user
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserDocuments(@PathVariable String userId) {
        try {
            List<UserDoc> documents = documentService.getUserDocuments(userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "documents", documents,
                    "count", documents.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Failed to fetch documents: " + e.getMessage()
                    ));
        }
    }

    // ✅ Get specific document
    @GetMapping("/{userId}/{docType}")
    public ResponseEntity<?> getDocument(
            @PathVariable String userId,
            @PathVariable DocType docType) {
        try {
            var doc = documentService.getDocument(userId, docType);
            if (doc.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "success", false,
                                "error", "Document not found"
                        ));
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "document", doc.get()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Failed to fetch document: " + e.getMessage()
                    ));
        }
    }

    // ✅ Delete document
    @DeleteMapping("/{userId}/{docType}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String userId,
            @PathVariable DocType docType) {
        try {
            documentService.deleteDocument(userId, docType);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Document deleted successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Failed to delete document: " + e.getMessage()
                    ));
        }
    }
}