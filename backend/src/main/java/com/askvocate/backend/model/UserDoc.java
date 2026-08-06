package com.askvocate.backend.model;

import com.askvocate.backend.entity.DocStatus;
import com.askvocate.backend.entity.DocType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// JSON FILE TEMPLATE FOR EACH DOCUMENT STORING IN "documents" COLLECTION/FOLDER IN FIREBASE.


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class UserDoc {
    private String id;                    // identical to docType.name()
    private String userId;
    private DocType docType;
    private String fileUrl;
    private String storagePath;
    private boolean ocrProcessed;
    private String extractedData;
    private boolean tamperFlagged;
    private Double tamperConfidence;
    private DocStatus status;
    private Long uploadedAt;
}
