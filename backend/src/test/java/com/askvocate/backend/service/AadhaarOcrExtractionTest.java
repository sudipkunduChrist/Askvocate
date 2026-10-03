package com.askvocate.backend.service;

import com.askvocate.backend.model.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AadhaarOcrExtractionTest {
    private final OcrExtractionService ocr = new OcrExtractionService();

    @Test
    void readsCloudinaryFullTextAnnotationDescription() {
        Map<String, Object> response = Map.of("ocr", Map.of("adv_ocr", Map.of("data", List.of(
                Map.of("textAnnotations", List.of(Map.of("description", "Aadhaar\nTest Lawyer")))))));
        assertEquals("Aadhaar\nTest Lawyer", ocr.extractTextFromOcrResponse(response));
    }

    @Test
    void extractsDistinctFrontAndBackFieldsWithoutUsingIssueDateAsDob() {
        String number = validAadhaarNumber();
        var front = ocr.extract("Government of India\nTest Lawyer\nDOB: 01/02/1990\nFemale\n"
                + number.substring(0, 4) + " " + number.substring(4, 8) + " " + number.substring(8),
                DocumentType.AADHAAR, "front");
        var back = ocr.extract("Unique Identification Authority of India\nAddress: 12 Test Road\n"
                + "New Delhi 110001", DocumentType.AADHAAR, "back");
        var merged = ocr.mergeResults(front, back);

        assertTrue(front.success(), front.error());
        assertTrue(back.success(), back.error());
        assertTrue(merged.success(), merged.error());
        assertEquals("Test Lawyer", merged.extractedFields().get("name"));
        assertEquals("01/02/1990", merged.extractedFields().get("dob"));
        assertEquals("110001", merged.extractedFields().get("pincode"));
        assertEquals("XXXX-XXXX-" + number.substring(8), merged.maskedDocumentNumber());
        assertEquals(number, merged.aadhaarNumber());
        assertFalse(merged.extractedFields().containsKey("aadhaarNumber"));
    }

    @Test
    void acceptsYearOfBirthButRejectsIssueDateAsBirthDate() {
        String number = validAadhaarNumber();
        var yob = ocr.extract("Aadhaar\nName: Test Lawyer\nYOB: 1990\nMale\n" + number,
                DocumentType.AADHAAR, "front");
        var issueOnly = ocr.extract("Aadhaar\nName: Test Lawyer\nIssue Date: 01/02/2020\nMale\n" + number,
                DocumentType.AADHAAR, "front");
        assertTrue(yob.success(), yob.error());
        assertEquals("1990", yob.extractedFields().get("yearOfBirth"));
        assertFalse(issueOnly.success());
        assertFalse(issueOnly.extractedFields().containsKey("dob"));
    }

    @Test
    void rejectsOtherDocumentAndInvalidAadhaarNumber() {
        var unrelated = ocr.extract("Name: Test Lawyer\nAddress: 12 Test Road 110001\n"
                + "DOB: 01/02/1990\nMale", DocumentType.AADHAAR, "front");
        var invalid = ocr.extract("Aadhaar\nName: Test Lawyer\nDOB: 01/02/1990\nMale\n2345 6789 0123",
                DocumentType.AADHAAR, "front");
        assertFalse(unrelated.success());
        assertFalse(invalid.success());
        assertNull(invalid.maskedDocumentNumber());
    }

    @Test
    void readsHindiPataFieldAndStopsAtPincode() {
        var back = ocr.extract("Unique Identification Authority of India\nपता:\n"
                + "घर 12, मुख्य मार्ग\nनई दिल्ली 110001\n1947\nwww.uidai.gov.in",
                DocumentType.AADHAAR, "back");

        assertTrue(back.success(), back.error());
        assertEquals("घर 12, मुख्य मार्ग नई दिल्ली 110001", back.extractedFields().get("address"));
        assertEquals("110001", back.extractedFields().get("pincode"));
    }

    @Test
    void prefersEnglishAddressWhenBothLabelsExistAndRejectsUnlabeledPin() {
        var bilingual = ocr.extract("UIDAI\nपता: घर 12, मुख्य मार्ग 110001\n"
                + "Address: 12 Main Road\nNew Delhi 110001\nAadhaar 2345 6789 0123",
                DocumentType.AADHAAR, "back");
        var unlabeled = ocr.extract("UIDAI\n12 Main Road\nNew Delhi 110001",
                DocumentType.AADHAAR, "back");

        assertTrue(bilingual.success(), bilingual.error());
        assertEquals("12 Main Road New Delhi 110001", bilingual.extractedFields().get("address"));
        assertFalse(unlabeled.success());
        assertFalse(unlabeled.extractedFields().containsKey("address"));
    }

    private String validAadhaarNumber() {
        String prefix = "23456789012";
        for (int digit = 0; digit < 10; digit++) {
            String candidate = prefix + digit;
            if (ocr.isValidAadhaarChecksum(candidate)) return candidate;
        }
        throw new AssertionError("Unable to create checksum-valid test number");
    }
}
