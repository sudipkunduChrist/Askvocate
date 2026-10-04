package com.askvocate.backend.service;

import com.askvocate.backend.model.DocumentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PanOcrExtractionTest {
    private final OcrExtractionService ocr = new OcrExtractionService();

    @Test
    void extractsLabeledPanFieldsAndFullNumber() {
        var result = ocr.extract("INCOME TAX DEPARTMENT\nPermanent Account Number\nAAAPL1234C\n"
                + "Name\nTest Lawyer\nFather's Name\nTest Father\nDate of Birth\n01/02/1990",
                DocumentType.PAN, "front");

        assertTrue(result.success(), result.error());
        assertEquals("Test Lawyer", result.extractedFields().get("name"));
        assertEquals("Test Father", result.extractedFields().get("fatherName"));
        assertEquals("01/02/1990", result.extractedFields().get("dob"));
        assertEquals("Individual", result.extractedFields().get("panType"));
        assertEquals("AAAPL1234C", result.panNumber());
        assertEquals("XXXXXX234C", result.maskedDocumentNumber());
        assertFalse(result.extractedFields().containsKey("panNumber"));
    }

    @Test
    void fatherNameIsNotMistakenForCardholderName() {
        var result = ocr.extract("PAN Card\nAAAPL1234C\nFather's Name: Test Father\n"
                + "Date of Birth: 01/02/1990", DocumentType.PAN, "front");

        assertFalse(result.success());
        assertEquals("Could not extract the PAN cardholder name.", result.error());
        assertNull(result.panNumber());
    }

    @Test
    void bilingualPrintedLabelsAreSkippedBeforeReadingActualNames() {
        var result = ocr.extract("आयकर विभाग\nPermanent Account Number\nAAAPL1234C\n"
                + "नाम / Name\nTest Lawyer\nपिता का नाम / Father's Name\nTest Father\n"
                + "जन्म तिथि / Date of Birth\n26/02/2006", DocumentType.PAN, "front");

        assertTrue(result.success(), result.error());
        assertEquals("Test Lawyer", result.extractedFields().get("name"));
        assertEquals("Test Father", result.extractedFields().get("fatherName"));
        assertEquals("26/02/2006", result.extractedFields().get("dob"));
    }

    @Test
    void labelWordsAloneCannotBeSavedAsNames() {
        var result = ocr.extract("PAN Card\nAAAPL1234C\nनाम / Name\n"
                + "पिता का नाम / Father's Name\nDOB: 26/02/2006", DocumentType.PAN, "front");

        assertFalse(result.success());
        assertNotEquals("Name", result.extractedFields().get("name"));
        assertNotEquals("Father's Name", result.extractedFields().get("fatherName"));
    }

    @Test
    void rejectsOtherDocumentCorporatePanAndIssueDate() {
        var noMarker = ocr.extract("Name: Test Lawyer\nFather's Name: Test Father\n"
                + "DOB: 01/02/1990\nAAAPL1234C", DocumentType.PAN, "front");
        var company = ocr.extract("PAN Card\nAAACL1234C\nName: Test Lawyer\n"
                + "Father's Name: Test Father\nDOB: 01/02/1990", DocumentType.PAN, "front");
        var issueOnly = ocr.extract("PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                + "Father's Name: Test Father\nIssue Date: 01/02/2020", DocumentType.PAN, "front");

        assertFalse(noMarker.success());
        assertFalse(company.success());
        assertTrue(company.error().contains("individual"));
        assertFalse(issueOnly.success());
        assertFalse(issueOnly.extractedFields().containsKey("dob"));
    }
}
