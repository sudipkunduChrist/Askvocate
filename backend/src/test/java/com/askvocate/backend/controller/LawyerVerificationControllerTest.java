package com.askvocate.backend.controller;

import com.askvocate.backend.dto.BarCouncilVerificationRequest;
import com.askvocate.backend.dto.DocumentVerificationResponse;
import com.askvocate.backend.dto.LawyerVerificationSummaryResponse;
import com.askvocate.backend.entity.Verification_Status;
import com.askvocate.backend.model.DocumentType;
import com.askvocate.backend.model.VerificationStatus;
import com.askvocate.backend.service.DocumentVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LawyerVerificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DocumentVerificationService verificationService;

    @InjectMocks
    private LawyerVerificationController lawyerVerificationController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(lawyerVerificationController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/lawyers/verification/verify-document - Should accept multipart document upload")
    void testVerifyLawyerDocument() throws Exception {
        MockMultipartFile front = new MockMultipartFile(
                "front", "bar_id_front.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-front-bytes".getBytes()
        );

        DocumentVerificationResponse mockResponse = DocumentVerificationResponse.from(
                "doc-123",
                DocumentType.BAR_COUNCIL_ID,
                VerificationStatus.VERIFIED,
                "D/XXXX/2021",
                Map.of("name", "Adv. Rohit Sharma", "enrollmentNumber", "D/1234/2021"),
                0.95,
                null,
                null,
                "VERIFIED",
                true,
                "Bar Council credentials verified successfully! Advocate profile verified."
        );

        when(verificationService.verifyDocument(eq("lawyer-1"), eq(DocumentType.BAR_COUNCIL_ID), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/lawyers/verification/verify-document")
                        .file(front)
                        .param("userId", "lawyer-1")
                        .param("documentType", "BAR_COUNCIL_ID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-123"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.lawyerVerificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.nameMatched").value(true));
    }

    @Test
    @DisplayName("POST /api/lawyers/verification/verify-bar-council - Should verify direct Bar Council enrollment number")
    void testVerifyBarCouncilDirect() throws Exception {
        BarCouncilVerificationRequest request = new BarCouncilVerificationRequest();
        request.setUserId("lawyer-1");
        request.setBarCouncilNumber("KAR/12345/2020");
        request.setAdvocateName("Suresh Kumar");

        DocumentVerificationResponse mockResponse = DocumentVerificationResponse.from(
                "doc-456",
                DocumentType.BAR_COUNCIL_ID,
                VerificationStatus.VERIFIED,
                "KAR/XXXXX/2020",
                Map.of("enrollmentNumber", "KAR/12345/2020", "stateCouncil", "Bar Council of Karnataka"),
                1.0,
                null,
                null,
                "VERIFIED",
                true,
                "Bar Council credentials verified successfully! Advocate profile verified."
        );

        when(verificationService.verifyBarCouncilDirect(any(BarCouncilVerificationRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/lawyers/verification/verify-bar-council")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.maskedDocumentNumber").value("KAR/XXXXX/2020"));
    }

    @Test
    @DisplayName("GET /api/lawyers/verification/{userId}/summary - Should return summary")
    void testGetVerificationSummary() throws Exception {
        LawyerVerificationSummaryResponse mockSummary = LawyerVerificationSummaryResponse.builder()
                .userId("lawyer-1")
                .lawyerName("Adv. Rohit Sharma")
                .email("rohit@example.com")
                .role("LAWYER_EXPERIENCED")
                .verificationStatus(Verification_Status.VERIFIED)
                .barCouncilId("D/1234/2021")
                .completionPercentage(100)
                .barCouncilVerified(true)
                .identityVerified(true)
                .nextStep("Your documents are fully verified. Your advocate profile is active!")
                .verifiedDocuments(List.of())
                .summaryDetails(Map.of("isFullyVerified", true))
                .build();

        when(verificationService.getLawyerVerificationSummary("lawyer-1"))
                .thenReturn(mockSummary);

        mockMvc.perform(get("/api/lawyers/verification/lawyer-1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("lawyer-1"))
                .andExpect(jsonPath("$.completionPercentage").value(100))
                .andExpect(jsonPath("$.barCouncilVerified").value(true))
                .andExpect(jsonPath("$.identityVerified").value(true));
    }
}
