package com.askvocate.backend.service;

import com.askvocate.backend.model.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PanOcrExtractionTest {
    private final OcrExtractionService ocr = new OcrExtractionService();

    @Test
    void readsOcrConfusedDobLabelsAndSpacedDates() {
        for (String label : List.of("DOB", "D.O.B", "D O B", "D0B", "Birth Date")) {
            var result = ocr.extract("PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                            + "Father's Name: Test Father\n" + label + ": 01 / 02 / 1990",
                    DocumentType.PAN, "front");
            assertTrue(result.success(), label + ": " + result.error());
            assertEquals("01/02/1990", result.extractedFields().get("dob"), label);
        }
    }

    @Test
    void readsDobWhenBirthLabelHasOcrNoise() {
        for (String label : List.of("Date of Blrth", "ate of Birth", "B1rth Date")) {
            var result = ocr.extract("PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                            + "Father's Name: Test Father\n" + label + ": 01/02/1990",
                    DocumentType.PAN, "front");
            assertTrue(result.success(), label + ": " + result.error());
            assertEquals("01/02/1990", result.extractedFields().get("dob"), label);
        }
    }

    @Test
    void readsDobValueOnFollowingLine() {
        var result = ocr.extract("PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                        + "Father's Name: Test Father\nDOB\n01/02/1990",
                DocumentType.PAN, "front");

        assertTrue(result.success(), result.error());
        assertEquals("01/02/1990", result.extractedFields().get("dob"));
    }

    @Test
    void triesAllOcrCandidatesAndPicksTheCompleteRead() {
        var raw = java.util.Map.<String, Object>of(
                "ocrText", "Name: Test Lawyer\nFather's Name: Test Father",
                "candidates", List.of(
                        "Name: Test Lawyer\nFather's Name: Test Father",
                        "PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                                + "Father's Name: Test Father\nDOB: 01/02/1990"),
                "confidence", 0.8);

        var result = ocr.extract(raw, DocumentType.PAN, "front");

        assertTrue(result.success(), result.error());
        assertEquals("AAAPL1234C", result.panNumber());
        assertEquals("01/02/1990", result.extractedFields().get("dob"));
    }

    @Test
    void rejectsGarbledPanFatherName() {
        var result = ocr.extract("PAN Card\nAAAPL1234C\nName: SHIVANG BAJAJ\n"
                        + "Father's Name: ar Bt arte\nDOB: 01/02/1990",
                DocumentType.PAN, "front");

        assertFalse(result.success());
        assertFalse(result.extractedFields().containsKey("fatherName"));
    }

    @Test
    void acceptsInitialBasedFatherName() {
        var result = ocr.extract("PAN Card\nAAAPL1234C\nName: SHIVANG BAJAJ\n"
                        + "Father's Name: R K Sharma\nDOB: 01/02/1990",
                DocumentType.PAN, "front");

        assertTrue(result.success(), result.error());
        assertEquals("R K Sharma", result.extractedFields().get("fatherName"));
    }

    @Test
    void rejectsUnknownPanHolderTypeAsInvalid() {
        var result = ocr.extract("PAN Card\nABCDE1234F\nName: Rahul Kumar\n"
                        + "Father's Name: Suresh Kumar\nDOB: 15/08/1994",
                DocumentType.PAN, "front");

        assertFalse(result.success());
        assertTrue(result.error().toLowerCase().contains("invalid"));
        assertFalse(result.error().contains("Only an individual"));
    }

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
    void readsUnlabeledCardholderNameAboveFatherLabel() {
        var result = ocr.extract("INCOME TAX DEPARTMENT\nAAAPL1234C\n"
                + "Test Lawyer\nFather's Name: Test Father\nDOB: 01/02/1990",
                DocumentType.PAN, "front");

        assertTrue(result.success(), result.error());
        assertEquals("Test Lawyer", result.extractedFields().get("name"));
        assertEquals("Test Father", result.extractedFields().get("fatherName"));
    }

    @Test
    void readsTwoUnlabeledNamesBetweenPanNumberAndDobInPrintedOrder() {
        var result = ocr.extract("INCOME TAX DEPARTMENT\nAAAPL1234C\n"
                + "Test Lawyer\nTest Father\nDate of Birth: 01/02/1990",
                DocumentType.PAN, "front");

        assertTrue(result.success(), result.error());
        assertEquals("Test Lawyer", result.extractedFields().get("name"));
        assertEquals("Test Father", result.extractedFields().get("fatherName"));
    }

    @Test
    void missingNameDoesNotSubstitutePanHeadingOrFatherName() {
        var result = ocr.extract("INCOME TAX DEPARTMENT\nGOVT OF INDIA\nAAAPL1234C\n"
                + "Father's Name: Test Father\nDOB: 01/02/1990",
                DocumentType.PAN, "front");

        assertFalse(result.success());
        assertFalse(result.extractedFields().containsKey("name"));
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
    void acceptsPanWithoutHeadingButRejectsCorporatePanAndIssueDate() {
        var noMarker = ocr.extract("Name: Test Lawyer\nFather's Name: Test Father\n"
                + "DOB: 01/02/1990\nAAAPL1234C", DocumentType.PAN, "front");
        var company = ocr.extract("PAN Card\nAAACL1234C\nName: Test Lawyer\n"
                + "Father's Name: Test Father\nDOB: 01/02/1990", DocumentType.PAN, "front");
        var issueOnly = ocr.extract("PAN Card\nAAAPL1234C\nName: Test Lawyer\n"
                + "Father's Name: Test Father\nIssue Date: 01/02/2020", DocumentType.PAN, "front");

        assertTrue(noMarker.success(), noMarker.error());
        assertEquals("AAAPL1234C", noMarker.panNumber());
        assertFalse(company.success());
        assertTrue(company.error().contains("individual"));
        assertFalse(issueOnly.success());
        assertFalse(issueOnly.extractedFields().containsKey("dob"));
    }
}
