package com.askvocate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for Google Sign-In: the app sends the Google ID token (JWT)
 * obtained from Credential Manager. The server verifies it against Google's
 * public keys and extracts the user profile from its claims.
 */
@Data
public class GoogleAuthRequest {
    @NotBlank(message = "Google ID token is required")
    private String idToken;

    /** target role when signing up via Google ("CLIENT", "LAWYER_FRESHER", "LAWYER_EXPERIENCED") */
    private String targetRole;
}
