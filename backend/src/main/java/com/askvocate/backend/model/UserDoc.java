package com.askvocate.backend.model;

import com.askvocate.backend.entity.DocStatus;
import com.askvocate.backend.entity.DocType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

// MongoDB document representation for user-uploaded documents (collection: "documents").

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("documents")
public class UserDoc {
    @Id
    private String id;                    // can hold string ObjectId or custom id (e.g., docType.name())
    private String userId;
    private DocType docType;
    private String fileUrl;
    private String storagePath;

    @Builder.Default
    private boolean ocrProcessed = false;

    private Map<String, Object> extractedData;

    @Builder.Default
    private boolean tamperFlagged = false;

    private Double tamperConfidence;
    private DocStatus status;

    @Builder.Default
    private Long uploadedAt = Instant.now().toEpochMilli();
}
