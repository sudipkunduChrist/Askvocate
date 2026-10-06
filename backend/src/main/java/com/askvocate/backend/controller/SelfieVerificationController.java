package com.askvocate.backend.controller;

import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.SelfieVerification;
import com.askvocate.backend.model.VerificationStatus;
import com.askvocate.backend.service.SelfieVerificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

@RestController
@RequestMapping("/api/documents/selfie")
public class SelfieVerificationController {
    private final SelfieVerificationService service;

    public SelfieVerificationController(SelfieVerificationService service) { this.service = service; }

    @PostMapping("/challenge")
    public ChallengeResponse challenge(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String userId) {
        SelfieVerification attempt = service.createChallenge(resolve(jwt, userId));
        return new ChallengeResponse(attempt.getChallengeId(), attempt.getExpectedTurn(),
                attempt.getChallengeExpiresAt());
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultResponse verify(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String userId,
            @RequestParam String challengeId, @RequestParam MultipartFile center,
            @RequestParam MultipartFile turned, @RequestParam MultipartFile returned) {
        SelfieVerification result = service.verify(resolve(jwt, userId), challengeId, center, turned, returned);
        return new ResultResponse(result.getId(), result.getVerificationStatus(), result.getFaceMatchScore(),
                result.getFaceMatchThreshold(), result.getLivenessPassed(), result.getLivenessScore(),
                result.getSelfieImageUrl(), result.getFailureReason());
    }

    private String resolve(Jwt jwt, String userId) {
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            if (userId != null && !userId.isBlank() && !userId.trim().equals(jwt.getSubject())) {
                throw new DocumentVerificationException("User ID does not match authenticated user.");
            }
            return jwt.getSubject();
        }
        if (userId == null || userId.isBlank()) throw new DocumentVerificationException("Invalid userID.");
        return userId.trim();
    }

    public record ChallengeResponse(String challengeId, String expectedTurn, Instant expiresAt) {}
    public record ResultResponse(String selfieId, VerificationStatus verificationStatus, Double faceMatchScore,
            Double faceMatchThreshold, Boolean livenessPassed, Double livenessScore,
            String selfieImageUrl, String failureReason) {}
}
