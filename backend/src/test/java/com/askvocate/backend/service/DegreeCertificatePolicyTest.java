package com.askvocate.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DegreeCertificatePolicyTest {

    private final DegreeCertificatePolicy policy =
            new DegreeCertificatePolicy(new DegreeInstitutionAllowlist());

    private static final String VALID_LLB =
            "University of Delhi\nFaculty of Law\nBachelor of Laws (LL.B.)\nName: Test Lawyer\n2020";

    @Test
    void rejectsMasterOfBusinessLaws() {
        var decision = policy.evaluate("Master of Business Laws (MBL)\nName: Test Lawyer", "Test Lawyer", "Test Lawyer", 0.95);
        assertTrue(decision.rejected());
        assertEquals("DEGREE_WRONG_LEVEL", decision.reasonCode());
        assertTrue(decision.userMessage().contains("Master of Business Laws"));
    }

    @Test
    void rejectsMasterOfLaws() {
        var decision = policy.evaluate("Master of Laws (LL.M.)\nName: Test Lawyer", "Test Lawyer", "Test Lawyer", 0.95);
        assertTrue(decision.rejected());
        assertEquals("DEGREE_WRONG_LEVEL", decision.reasonCode());
    }

    @Test
    void rejectsDiplomaProgrammes() {
        var decision = policy.evaluate("PG Diploma in Legal Studies\nName: Test Lawyer", "Test Lawyer", "Test Lawyer", 0.95);
        assertTrue(decision.rejected());
        assertEquals("DEGREE_WRONG_TYPE", decision.reasonCode());
    }

    @Test
    void rejectsMarksheets() {
        var decision = policy.evaluate("Consolidated Marksheet\nName: Test Lawyer", "Test Lawyer", "Test Lawyer", 0.95);
        assertTrue(decision.rejected());
        assertEquals("DEGREE_MARKSHEET", decision.reasonCode());
        assertTrue(decision.userMessage().contains("marksheets"));
    }

    @Test
    void rejectsHindiMarksheets() {
        var decision = policy.evaluate("अंकसूची\nName: Test Lawyer", "Test Lawyer", "Test Lawyer", 0.95);
        assertTrue(decision.rejected());
        assertEquals("DEGREE_MARKSHEET", decision.reasonCode());
    }

    @Test
    void rejectsDistanceLearning() {
        var decision = policy.evaluate("Distance Learning LL.B.\nName: Test Lawyer", "Test Lawyer", "Test Lawyer", 0.95);
        assertTrue(decision.rejected());
        assertEquals("DEGREE_INVALID_MODE", decision.reasonCode());
    }

    @Test
    void flagsForeignDegreesForManualReview() {
        var decision = policy.evaluate("University of Gondar\nEthiopia\nBachelor of Laws (LL.B.)\nName: Test Lawyer",
                "Test Lawyer", "Test Lawyer", 0.95);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_FOREIGN", decision.reasonCode());
        assertTrue(decision.flags().contains("REQUIRES_BCI_EQUIVALENCE"));
    }

    @Test
    void flagsContinuingEducationForManualReview() {
        var decision = policy.evaluate("Continuing Education\nExecutive Programme LL.B.\nName: Test Lawyer",
                "Test Lawyer", "Test Lawyer", 0.95);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_CONTINUING_EDUCATION", decision.reasonCode());
    }

    @Test
    void flagsLowNameMatchForManualReview() {
        var decision = policy.evaluate(VALID_LLB, "Different Person", "Test Lawyer", 0.95);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_LOW_NAME_MATCH", decision.reasonCode());
    }

    @Test
    void flagsUnconfirmedLlbForManualReview() {
        var decision = policy.evaluate("University of Delhi\nName: Test Lawyer\n2020",
                "Test Lawyer", "Test Lawyer", 0.95);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_UNCONFIRMED_LLB", decision.reasonCode());
    }

    @Test
    void flagsUnrecognizedUniversityForManualReview() {
        var decision = policy.evaluate("Some Random Institute\nBachelor of Laws (LL.B.)\nName: Test Lawyer",
                "Test Lawyer", "Test Lawyer", 0.95);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_UNRECOGNIZED_UNIVERSITY", decision.reasonCode());
    }

    @Test
    void flagsLowConfidenceForManualReview() {
        var decision = policy.evaluate(VALID_LLB, "Test Lawyer", "Test Lawyer", 0.25);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_LOW_OCR_CONFIDENCE", decision.reasonCode());
    }

    @Test
    void acceptsKarnatakaStateLawUniversity() {
        var decision = policy.evaluate(
                "Karnataka State Law University, Hubballi\n"
                        + "Certifies that\nAyushi S\nBachelor of Arts and Bachelor of Laws\nYear: 2020",
                "Ayushi S", "Ayushi Sharma", 0.48);
        assertEquals(DegreeCertificatePolicy.Outcome.ACCEPT, decision.outcome());
    }

    @Test
    void acceptsIntegratedBbaLlbAtRecognizedUniversity() {
        var decision = policy.evaluate(
                "University of Delhi\nFaculty of Law\nB.B.A. LL.B. (Hons.)\nName: Test Lawyer",
                "Test Lawyer", "Test Lawyer", 0.95);
        assertEquals(DegreeCertificatePolicy.Outcome.ACCEPT, decision.outcome());
    }

    @Test
    void allowlistMatchesCommonOcrVariantsOfBharatiVidyapeeth() {
        var allowlist = new DegreeInstitutionAllowlist();
        assertNotNull(allowlist.match("Bharali Vidyapoolh\nNew Law College, Pune"));
        assertNotNull(allowlist.match("Ghanati Vidyapeeth's\nNew Law College, Pune"));
        assertNotNull(allowlist.match("Bharati Vidyapecth's\nNew Law College, Pune"));
    }

    @Test
    void bharatiCertificateReachesNameCheckInsteadOfUnrecognizedUniversity() {
        var decision = policy.evaluate(
                "Bharali Vidyapoolh\nDeemed University, Pune (India)\n"
                        + "Certify that\nSin0cta Mohapatra\nof Bharati Vidyapeeth's\n"
                        + "New Law College, Pune\nBachelor of Laws (Five Year Degree Course)",
                "Sin0cta Mohapatra", "Suneeta Mohapatra", 0.82);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_LOW_NAME_MATCH", decision.reasonCode());
    }

    @Test
    void acceptsBharatiWhenCourseAndApprovalYearMatch() {
        var evidence = new DegreeCertificatePolicy.DegreeEvidence(
                "Bharati Vidyapeeth's New Law College, Erandwana, Pune", "New Law College, Pune",
                "BACHELOR OF LAWS", "Five Year", "2015", "May-2014", null);
        var decision = policy.evaluate(
                "Bharati Vidyapeeth's New Law College, Pune\nBachelor of Laws (Five Year Degree Course)",
                "Suneeta Mohapatra", "Suneeta Mohapatra", 0.9, evidence);
        assertEquals(DegreeCertificatePolicy.Outcome.ACCEPT, decision.outcome());
    }

    @Test
    void flagsRecognizedUniversityWithUnapprovedCourse() {
        var evidence = new DegreeCertificatePolicy.DegreeEvidence(
                "Bharati Vidyapeeth's New Law College, Erandwana, Pune", "New Law College, Pune",
                "BACHELOR OF LAWS", "Three Year", "2015", "May-2014", null);
        var decision = policy.evaluate(
                "Bharati Vidyapeeth's New Law College, Pune\nBachelor of Laws (Three Year Course)",
                "Suneeta Mohapatra", "Suneeta Mohapatra", 0.9, evidence);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_COURSE_NOT_RECOGNIZED", decision.reasonCode());
    }

    @Test
    void flagsApprovalExpiredBeforeDegreeYear() {
        var evidence = new DegreeCertificatePolicy.DegreeEvidence(
                "Bharati Vidyapeeth's New Law College, Kolhapur", "New Law College, Kolhapur",
                "BACHELOR OF LAWS", "Five Year", "2025", "May-2025", null);
        var decision = policy.evaluate(
                "Bharati Vidyapeeth's New Law College, Kolhapur\nBachelor of Laws (Five Year Course)",
                "Suneeta Mohapatra", "Suneeta Mohapatra", 0.9, evidence);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_APPROVAL_EXPIRED", decision.reasonCode());
    }

    @Test
    void flagsFuzzyInstitutionMatchForManualReview() {
        var decision = policy.evaluate(
                "Gujrat National Law University, Gandhinagar\nBachelor of Laws",
                "Test Lawyer", "Test Lawyer", 0.9);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_FUZZY_INSTITUTION_MATCH", decision.reasonCode());
    }

    @Test
    void flagsFutureCertificateDateForManualReview() {
        var evidence = new DegreeCertificatePolicy.DegreeEvidence(
                "Bharati Vidyapeeth's New Law College, Erandwana, Pune", "New Law College, Pune",
                "BACHELOR OF LAWS", "Five Year", "2099", "2099", "05-11-2099");
        var decision = policy.evaluate(
                "Bharati Vidyapeeth's New Law College, Pune\nBachelor of Laws (Five Year Course)",
                "Suneeta Mohapatra", "Suneeta Mohapatra", 0.9, evidence);
        assertTrue(decision.manualReview());
        assertEquals("DEGREE_DATE_ANOMALY", decision.reasonCode());
    }

    @Test
    void acceptsInitializedName() {
        var decision = policy.evaluate(VALID_LLB, "T. Lawyer", "Test Lawyer", 0.95);
        assertEquals(DegreeCertificatePolicy.Outcome.ACCEPT, decision.outcome());
    }
}
