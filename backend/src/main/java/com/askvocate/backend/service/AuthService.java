package com.askvocate.backend.service;

import com.askvocate.backend.dto.LoginRequest;
import com.askvocate.backend.model.ClientProfile;
import com.askvocate.backend.model.LawyerExperiencedProfile;
import com.askvocate.backend.model.LawyerFresherProfile;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private ClientService clientService;

    @Autowired
    private LawyerFresherService lawyerFresherService;

    @Autowired
    private LawyerExperiencedService lawyerExperiencedService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @org.springframework.beans.factory.annotation.Value("${google.oauth.web-client-id}")
    private String googleWebClientId;

    /**
     * Backward-compatible overload without targetRole.
     */
    public Map<String, Object> loginWithGoogle(String idTokenString) {
        return loginWithGoogle(idTokenString, null);
    }

    /**
     * Verifies a Google ID token (from Android Credential Manager) and signs the user in.
     * If the email exists, signs into the existing role (or rejects if targetRole conflicts).
     * If new user, creates the profile matching targetRole ("CLIENT", "LAWYER_FRESHER", "LAWYER_EXPERIENCED").
     */
    public Map<String, Object> loginWithGoogle(String idTokenString, String targetRole) {
        GoogleIdToken.Payload payload = verifyGoogleIdToken(idTokenString);

        String email = payload.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google account has no email");
        }
        String name = (String) payload.get("name");
        if (name == null || name.isBlank()) {
            name = email.substring(0, email.indexOf('@'));
        }

        // Check if account already exists across any role collection
        Optional<ClientProfile> clientOpt = clientService.findByEmailOrPhone(email);
        Optional<LawyerFresherProfile> fresherOpt = lawyerFresherService.findByEmailOrPhone(email);
        Optional<LawyerExperiencedProfile> expOpt = lawyerExperiencedService.findByEmailOrPhone(email);

        String existingRole = null;
        Object existingUser = null;

        if (clientOpt.isPresent()) {
            existingRole = "CLIENT";
            existingUser = clientOpt.get();
        } else if (fresherOpt.isPresent()) {
            existingRole = "LAWYER_FRESHER";
            existingUser = fresherOpt.get();
        } else if (expOpt.isPresent()) {
            existingRole = "LAWYER_EXPERIENCED";
            existingUser = expOpt.get();
        }

        if (existingRole != null) {
            // If explicit targetRole was provided during sign-up and conflicts with existing account, reject
            if (targetRole != null && !targetRole.isBlank() && !targetRole.equalsIgnoreCase(existingRole)) {
                String roleName = existingRole.replace("_", " ").toLowerCase();
                throw new IllegalArgumentException("An account with this email already exists as a " + roleName + ". Please sign in instead.");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("role", existingRole);
            result.put("user", existingUser);
            result.put("isNewUser", false);
            return result;
        }

        // New User -> Create profile matching targetRole (default: CLIENT)
        String finalRole = (targetRole != null && !targetRole.isBlank()) ? targetRole.toUpperCase() : "CLIENT";
        Object newUserProfile;

        if ("LAWYER_FRESHER".equals(finalRole)) {
            LawyerFresherProfile profile = LawyerFresherProfile.builder()
                    .name(name)
                    .emailOrPhone(email)
                    .passwordHash(null)
                    .provider(com.askvocate.backend.entity.AuthProvider.GOOGLE)
                    .createdAt(java.time.Instant.now().toString())
                    .build();
            newUserProfile = lawyerFresherService.saveGoogleLawyerFresher(profile);
        } else if ("LAWYER_EXPERIENCED".equals(finalRole)) {
            LawyerExperiencedProfile profile = LawyerExperiencedProfile.builder()
                    .name(name)
                    .emailOrPhone(email)
                    .passwordHash(null)
                    .provider(com.askvocate.backend.entity.AuthProvider.GOOGLE)
                    .createdAt(java.time.Instant.now().toString())
                    .build();
            newUserProfile = lawyerExperiencedService.saveGoogleLawyerExperienced(profile);
        } else {
            ClientProfile profile = ClientProfile.builder()
                    .name(name)
                    .emailOrPhone(email)
                    .passwordHash(null)
                    .provider(com.askvocate.backend.entity.AuthProvider.GOOGLE)
                    .createdAt(java.time.Instant.now().toString())
                    .build();
            newUserProfile = clientService.saveGoogleClient(profile);
            finalRole = "CLIENT";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("role", finalRole);
        result.put("user", newUserProfile);
        result.put("isNewUser", true);
        return result;
    }

    private GoogleIdToken.Payload verifyGoogleIdToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(java.util.Collections.singletonList(googleWebClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid or expired Google token");
            }
            return idToken.getPayload();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not verify Google token: " + e.getMessage(), e);
        }
    }

    /**
     * Attempts login against Client, LawyerFresher, and LawyerExperienced collections.
     * Returns user profile + role if credentials match.
     */
    public Map<String, Object> login(LoginRequest dto) {
        String input = dto.getEmailOrPhone();
        String password = dto.getPassword();

        // 1. Check Client
        Optional<ClientProfile> clientOpt = clientService.findByEmailOrPhone(input);
        if (clientOpt.isPresent()) {
            ClientProfile client = clientOpt.get();
            if (client.getProvider() == com.askvocate.backend.entity.AuthProvider.GOOGLE) {
                // Federated account — no local password to check
                throw new IllegalArgumentException("This account uses Google Sign-In. Tap 'Sign in with Google' instead.");
            }
            if (passwordEncoder.matches(password, client.getPasswordHash())) {
                Map<String, Object> result = new HashMap<>();
                result.put("role", "CLIENT");
                result.put("user", client);
                return result;
            }
        }

        // 2. Check Lawyer Fresher
        Optional<LawyerFresherProfile> fresherOpt = lawyerFresherService.findByEmailOrPhone(input);
        if (fresherOpt.isPresent()) {
            LawyerFresherProfile fresher = fresherOpt.get();
            if (fresher.getProvider() == com.askvocate.backend.entity.AuthProvider.GOOGLE) {
                throw new IllegalArgumentException("This account uses Google Sign-In. Tap 'Sign in with Google' instead.");
            }
            if (passwordEncoder.matches(password, fresher.getPasswordHash())) {
                Map<String, Object> result = new HashMap<>();
                result.put("role", "LAWYER_FRESHER");
                result.put("user", fresher);
                return result;
            }
        }

        // 3. Check Lawyer Experienced
        Optional<LawyerExperiencedProfile> expOpt = lawyerExperiencedService.findByEmailOrPhone(input);
        if (expOpt.isPresent()) {
            LawyerExperiencedProfile exp = expOpt.get();
            if (exp.getProvider() == com.askvocate.backend.entity.AuthProvider.GOOGLE) {
                throw new IllegalArgumentException("This account uses Google Sign-In. Tap 'Sign in with Google' instead.");
            }
            if (passwordEncoder.matches(password, exp.getPasswordHash())) {
                Map<String, Object> result = new HashMap<>();
                result.put("role", "LAWYER_EXPERIENCED");
                result.put("user", exp);
                return result;
            }
        }

        throw new IllegalArgumentException("Invalid email/phone or password");
    }
}

