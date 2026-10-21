package com.askvocate.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BarCouncilValidationServiceTest {

    private BarCouncilValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new BarCouncilValidationService();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "D/1234/2021",
            "MAH/5678/2019",
            "UP/892/2018",
            "KAR/12345/2020",
            "TN/4321/2015",
            "WB/999/2022",
            "AP/50774/2017",
            "CG/30659/2006",
            "JH/78212/2012",
            "RJ/26924/2017"
    })
    @DisplayName("Should successfully validate standard Indian Bar Council enrollment numbers")
    void testValidEnrollmentNumbers(String enrollmentNumber) {
        var result = validationService.validate(enrollmentNumber);
        assertTrue(result.valid(), "Expected " + enrollmentNumber + " to be valid");
        assertNotNull(result.stateCode());
        assertNotNull(result.stateCouncil());
        assertNotNull(result.maskedNumber());
        assertTrue(result.maskedNumber().contains("XXXX"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "INVALID_NUMBER",
            "12345",
            "D/0/2021",
            "MAH/123/1800",
            "UP/123/2099"
    })
    @DisplayName("Should reject invalid or out-of-range Bar Council enrollment numbers")
    void testInvalidEnrollmentNumbers(String invalidNumber) {
        var result = validationService.validate(invalidNumber);
        assertFalse(result.valid(), "Expected " + invalidNumber + " to be invalid");
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("Should correctly match lawyer names tolerating honorifics and order")
    void testNameMatching() {
        assertTrue(validationService.isNameMatching("Adv. Rajesh Sharma", "Rajesh Sharma"));
        assertTrue(validationService.isNameMatching("Advocate Priya Mehta", "Priya Mehta"));
        assertTrue(validationService.isNameMatching("Mr. Amit Kumar Singh", "Amit Kumar Singh"));
        assertTrue(validationService.isNameMatching("Nidhi Bose", "Adv. Nidhi Bose"));
        assertFalse(validationService.isNameMatching("Suresh Verma", "Ramesh Gupta"));
    }
}
