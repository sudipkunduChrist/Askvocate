package com.askvocate.backend.controller;

import com.askvocate.backend.exception.DocumentVerificationException;
import com.askvocate.backend.model.SelfieVerification;
import com.askvocate.backend.model.VerificationStatus;
import com.askvocate.backend.service.SelfieVerificationService;
import com.askvocate.backend.service.AppSessionService;
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
    private final AppSessionService sessions;

    public SelfieVerificationController(SelfieVerificationService service, AppSessionService sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    @PostMapping("/challenge")
    public ChallengeResponse challenge(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String userId) {
        SelfieVerification attempt = service.createChallenge(resolve(jwt, authorization, userId));
        return new ChallengeResponse(attempt.getChallengeId(), attempt.getExpectedTurn(),
                attempt.getChallengeExpiresAt());
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultResponse verify(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String userId,
            @RequestParam String challengeId, @RequestParam MultipartFile center,
            @RequestParam MultipartFile turned, @RequestParam MultipartFile returned) {
        SelfieVerification result = service.verify(resolve(jwt, authorization, userId), challengeId, center, turned, returned);
        return new ResultResponse(result.getId(), result.getVerificationStatus(), result.getFaceMatchScore(),
                result.getFaceMatchThreshold(), result.getLivenessPassed(), result.getLivenessScore(),
                result.getSelfieImageUrl(), result.getFailureReason());
    }

    private String resolve(Jwt jwt, String authorization, String userId) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            var account = sessions.requireAccount(authorization);
            if ("CLIENT".equals(account.role())) throw new DocumentVerificationException("Lawyer account required.");
            if (userId != null && !userId.isBlank() && !userId.trim().equals(account.userId())) {
                throw new DocumentVerificationException("User ID does not match authenticated user.");
            }
            return account.userId();
        }
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            if (userId != null && !userId.isBlank() && !userId.trim().equals(jwt.getSubject())) {
                throw new DocumentVerificationException("User ID does not match authenticated user.");
            }
            return jwt.getSubject();
        }
        throw new DocumentVerificationException("Sign in required.");
    }

    public record ChallengeResponse(String challengeId, String expectedTurn, Instant expiresAt) {}
    public record ResultResponse(String selfieId, VerificationStatus verificationStatus, Double faceMatchScore,
            Double faceMatchThreshold, Boolean livenessPassed, Double livenessScore,
            String selfieImageUrl, String failureReason) {}
}
