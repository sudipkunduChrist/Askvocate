package com.askvocate.backend.service;

import com.askvocate.backend.model.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.util.*;

@Service
public class AwsTextractService {

    private static final Logger log = LoggerFactory.getLogger(AwsTextractService.class);

    private final TextractClient textractClient;
    private final boolean enabled;

    public record TextractResult(
            boolean success,
            String fullText,
            List<String> textLines,
            Map<String, String> queriedFields,
            double confidence
    ) {}

    public AwsTextractService(@Autowired(required = false) TextractClient textractClient,
                              @Value("${aws.textract.enabled:false}") boolean enabled) {
        this.textractClient = textractClient;
        this.enabled = enabled;
    }

    public boolean isAvailable() {
        return enabled && textractClient != null;
    }

    public Optional<TextractResult> extractDocument(byte[] imageBytes, DocumentType documentType) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        try {
            Document document = Document.builder()
                    .bytes(SdkBytes.fromByteArray(imageBytes))
                    .build();

            // Try queries feature first for high precision field extraction
            List<Query> queries = queriesForDocument(documentType);
            if (!queries.isEmpty()) {
                try {
                    AnalyzeDocumentRequest analyzeRequest = AnalyzeDocumentRequest.builder()
                            .document(document)
                            .featureTypes(FeatureType.QUERIES)
                            .queriesConfig(QueriesConfig.builder().queries(queries).build())
                            .build();

                    AnalyzeDocumentResponse response = textractClient.analyzeDocument(analyzeRequest);
                    return Optional.of(parseAnalyzeResponse(response));
                } catch (Exception qe) {
                    log.warn("Textract AnalyzeDocument queries failed or not supported in region, falling back to DetectDocumentText: {}", qe.getMessage());
                }
            }

            // Fallback to standard DetectDocumentText
            DetectDocumentTextRequest detectRequest = DetectDocumentTextRequest.builder()
                    .document(document)
                    .build();

            DetectDocumentTextResponse response = textractClient.detectDocumentText(detectRequest);
            return Optional.of(parseDetectResponse(response));
        } catch (Exception e) {
            log.error("AWS Textract extraction error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<Query> queriesForDocument(DocumentType documentType) {
        return switch (documentType) {
            case BAR_COUNCIL_ID, BAR_CERTIFICATE, CERTIFICATE_OF_PRACTICE -> List.of(
                    Query.builder().text("What is the advocate name?").alias("name").build(),
                    Query.builder().text("What is the enrollment number?").alias("enrollmentNumber").build(),
                    Query.builder().text("What is the bar council name?").alias("barCouncil").build()
            );
            case DEGREE_CERTIFICATE -> List.of(
                    Query.builder().text("What is the candidate name?").alias("name").build(),
                    Query.builder().text("What is the university or college name?").alias("institution").build(),
                    Query.builder().text("What is the year of passing or graduation?").alias("passingYear").build(),
                    Query.builder().text("What is the degree or roll number?").alias("degreeNumber").build()
            );
            case AIBE_CERTIFICATE -> List.of(
                    Query.builder().text("What is the candidate name?").alias("name").build(),
                    Query.builder().text("What is the AIBE roll number or registration number?").alias("aibeNumber").build(),
                    Query.builder().text("What is the year of examination?").alias("passingYear").build()
            );
            default -> List.of();
        };
    }

    private TextractResult parseAnalyzeResponse(AnalyzeDocumentResponse response) {
        StringBuilder fullText = new StringBuilder();
        List<String> lines = new ArrayList<>();
        Map<String, String> queryResults = new LinkedHashMap<>();
        Map<String, String> queryIdToAlias = new HashMap<>();
        Map<String, String> queryIdToAnswerId = new HashMap<>();
        Map<String, Block> blockMap = new HashMap<>();
        double totalConfidence = 0.0;
        int wordCount = 0;

        for (Block block : response.blocks()) {
            blockMap.put(block.id(), block);
            if (block.blockType() == BlockType.LINE && block.text() != null) {
                lines.add(block.text());
                fullText.append(block.text()).append("\n");
            }
            if (block.blockType() == BlockType.WORD && block.confidence() != null) {
                totalConfidence += block.confidence();
                wordCount++;
            }
            if (block.blockType() == BlockType.QUERY && block.query() != null) {
                String alias = block.query().alias();
                if (alias != null) {
                    queryIdToAlias.put(block.id(), alias);
                }
                if (block.relationships() != null) {
                    for (Relationship rel : block.relationships()) {
                        if (rel.type() == RelationshipType.ANSWER && rel.ids() != null) {
                            for (String ansId : rel.ids()) {
                                queryIdToAnswerId.put(block.id(), ansId);
                            }
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, String> entry : queryIdToAnswerId.entrySet()) {
            String queryId = entry.getKey();
            String answerId = entry.getValue();
            String alias = queryIdToAlias.get(queryId);
            Block answerBlock = blockMap.get(answerId);
            if (alias != null && answerBlock != null && answerBlock.text() != null) {
                queryResults.put(alias, answerBlock.text().trim());
            }
        }

        double confidence = wordCount > 0 ? (totalConfidence / wordCount) / 100.0 : 0.8;
        return new TextractResult(true, fullText.toString().trim(), lines, queryResults, confidence);
    }

    private TextractResult parseDetectResponse(DetectDocumentTextResponse response) {
        StringBuilder fullText = new StringBuilder();
        List<String> lines = new ArrayList<>();
        double totalConfidence = 0.0;
        int wordCount = 0;

        for (Block block : response.blocks()) {
            if (block.blockType() == BlockType.LINE && block.text() != null) {
                lines.add(block.text());
                fullText.append(block.text()).append("\n");
            }
            if (block.blockType() == BlockType.WORD && block.confidence() != null) {
                totalConfidence += block.confidence();
                wordCount++;
            }
        }

        double confidence = wordCount > 0 ? (totalConfidence / wordCount) / 100.0 : 0.8;
        return new TextractResult(true, fullText.toString().trim(), lines, Map.of(), confidence);
    }
}
