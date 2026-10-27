package com.askvocate.backend.service;

import com.askvocate.backend.model.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CertificateOcrExtractionTest {

    private final OcrExtractionService ocr = new OcrExtractionService();

    @Test
    void extractsDegreeCertificateFields() {
        String text = """
                UNIVERSITY OF DELHI
                FACULTY OF LAW
                PROVISIONAL DEGREE CERTIFICATE
                This is to certify that
                Name: Test Lawyer
                has successfully completed the
                Bachelor of Laws (LL.B.)
                Degree No: DU/LLB/2020/1234
                Year: 2020
                Registrar
                """;

        var result = ocr.extract(text, DocumentType.DEGREE_CERTIFICATE, "front");

        assertTrue(result.success());
        assertEquals("Test Lawyer", result.extractedFields().get("name"));
        assertEquals("2020", result.extractedFields().get("year"));
        assertEquals("PROVISIONAL", result.extractedFields().get("certificateType"));
        assertNotNull(result.extractedFields().get("degreeType"));
        assertTrue(result.extractedFields().get("degreeType").contains("LAWS")
                || result.extractedFields().get("degreeType").contains("LL.B"));
        assertEquals("DU/LLB/2020/1234", result.extractedFields().get("degreeNumber"));
        assertTrue(result.extractedFields().get("institution").toUpperCase().contains("UNIVERSITY"));
    }

    @Test
    void mergesNameAndInstitutionFromDifferentTesseractPasses() {
        String cleanPass = """
                Karnataka State Law University, Hubballi
                Bachelor of Arts and Bachelor of Laws
                Year: 2020
                Examination Registration Number
                42318231026
                College Name : MS Ramaiah College Of Law, Bangalore
                """;
        String namePass = "NATAKA LAVN UNIVE �Ayushi NIVERSITY KARNATAKA";
        String degreePass = "> Ayushi S\nhas been duly admitted to the Degree of\nBachelor of Arts and Bachelor of Laws";

        Map<String, Object> raw = Map.of(
                "ocrText", cleanPass,
                "candidates", List.of(cleanPass, namePass, degreePass),
                "nameCandidate", "Ayushi",
                "confidence", 0.85);

        var result = ocr.extract(raw, DocumentType.DEGREE_CERTIFICATE, "front");

        assertTrue(result.success());
        assertTrue(result.extractedFields().get("name").startsWith("Ayushi"));
        assertTrue(result.extractedFields().get("institution").contains("Karnataka State Law University"));
        assertTrue(result.extractedFields().get("college").contains("Ramaiah"));
        assertTrue(result.extractedFields().get("degreeType").contains("LAWS"));
        assertEquals("2020", result.extractedFields().get("year"));
    }

    @Test
    void extractsBharatiVidyapeethCertificateWithDigitInCursiveName() {
        String text = """
                Bhanati Vidyapecth's
                Deemed University, Pune (India)
                (Established u/s. 3 of the UGC Act 1956)
                Certify that
                Sin0cta Mohapatra
                of Bharati Vidyapeeth's
                New Law College, Pune
                Bachelor of Laws (Five Year Degree Course)
                May-2014 in First Class
                Class A (VERY GOOD)
                Given under the seal of the University and the
                Signatures of the Chancellor and the Vice-Chancellor
                """;

        var result = ocr.extract(text, DocumentType.DEGREE_CERTIFICATE, "front");

        assertTrue(result.success());
        assertEquals("Sin0cta Mohapatra", result.extractedFields().get("name"));
        assertFalse(result.extractedFields().get("name").toLowerCase().contains("vidyapecth"));
        assertTrue(result.extractedFields().get("college").contains("New Law College"));
        assertTrue(result.extractedFields().get("institution").contains("New Law College"));
        assertEquals("2014", result.extractedFields().get("year"));
        assertEquals("A (VERY GOOD)", result.extractedFields().get("classObtained"));
    }

    @Test
    void extractsClassWithGrade() {
        String text = """
                Karnataka State Law University, Hubballi
                Bachelor of Arts and Bachelor of Laws
                Name: Ayushi
                Class A (VERY GOOD)
                """;
        var result = ocr.extract(text, DocumentType.DEGREE_CERTIFICATE, "front");
        assertEquals("A (VERY GOOD)", result.extractedFields().get("classObtained"));
    }

    @Test
    void extractsNameFromNoisyLowResolutionLine() {
        String cleanPass = """
                Karnataka State Law University, Hubballi
                Bachelor of Arts and Bachelor of Laws
                College Name : MS Ramaiah College Of Law, Bangalore
                """;
        String noisyPass = "Bead tie Ren oepoees. ay Ps Ayushi bi HL? Tere Oey, beet Ge ser, Za. Bl";
        Map<String, Object> raw = Map.of(
                "ocrText", cleanPass,
                "candidates", List.of(cleanPass, noisyPass),
                "nameCandidate", "Wet",
                "confidence", 0.48);

        var result = ocr.extract(raw, DocumentType.DEGREE_CERTIFICATE, "front");

        assertTrue(result.success());
        assertTrue(result.extractedFields().get("name").startsWith("Ayushi"));
        assertEquals("Ayushi", ocr.bestNameFor(raw, "Ayushi"));
        assertTrue(result.extractedFields().get("institution").contains("Karnataka State Law University"));
    }

    @Test
    void extractsAibeCertificateFields() {
        String text = """
                ALL INDIA BAR EXAMINATION
                Name: Test Lawyer
                AIBE Roll No: AIBE/2021/5678
                Year: 2021
                """;

        var result = ocr.extract(text, DocumentType.AIBE_CERTIFICATE, "front");

        assertTrue(result.success());
        assertEquals("Test Lawyer", result.extractedFields().get("name"));
        assertEquals("AIBE/2021/5678", result.extractedFields().get("aibeNumber"));
        assertEquals("2021", result.extractedFields().get("year"));
    }
}
