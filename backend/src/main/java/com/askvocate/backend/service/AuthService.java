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

    @Autowired
    private EmailAccountService emailAccountService;

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
     * One email owns exactly one account across the client, fresher and experienced
     * collections; requesting a different role with the same email is rejected.
     */
    public Map<String, Object> loginWithGoogle(String idTokenString, String targetRole) {
        GoogleIdToken.Payload payload = verifyGoogleIdToken(idTokenString);

        String email = EmailAccountService.normalize(payload.getEmail());
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google account has no email");
        }
        String name = (String) payload.get("name");
        if (name == null || name.isBlank()) {
            name = email.substring(0, email.indexOf('@'));
        }

        String requestedRole = targetRole == null ? "" : targetRole.trim().toUpperCase();
        if (!requestedRole.isEmpty()
                && !requestedRole.equals("CLIENT")
                && !requestedRole.equals("LAWYER_FRESHER")
                && !requestedRole.equals("LAWYER_EXPERIENCED")) {
            throw new IllegalArgumentException("Unsupported account role");
        }

        String existingRole = null;
        Object existingUser = null;

        // One email = one account across all role collections.
        var account = emailAccountService.findAccount(email);
        if (account.isPresent()) {
            existingRole = account.get().role();
            existingUser = account.get().user();
            if (!requestedRole.isEmpty() && !requestedRole.equals(existingRole)) {
                throw new IllegalArgumentException(
                        "An account with this email already exists. Sign in with that account, "
                        + "or use a different email.");
            }
        }

        if (existingRole != null) {
            // Migrate legacy documents that stored the address under emailOrPhone.
            if (existingUser instanceof ClientProfile client && client.getEmail() == null) {
                client.setEmail(email);
                existingUser = clientService.saveGoogleClient(client);
            } else if (existingUser instanceof LawyerFresherProfile fresher && fresher.getEmail() == null) {
                fresher.setEmail(email);
                existingUser = lawyerFresherService.saveGoogleLawyerFresher(fresher);
            } else if (existingUser instanceof LawyerExperiencedProfile experienced && experienced.getEmail() == null) {
                experienced.setEmail(email);
                existingUser = lawyerExperiencedService.saveGoogleLawyerExperienced(experienced);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("role", existingRole);
            result.put("user", existingUser);
            result.put("isNewUser", false);
            result.put("profileImageUrl", payload.get("picture"));
            return result;
        }

        // New User -> Create profile matching targetRole (default: CLIENT)
        String finalRole = requestedRole.isEmpty() ? "CLIENT" : requestedRole;
        Object newUserProfile;

        if ("LAWYER_FRESHER".equals(finalRole)) {
            LawyerFresherProfile profile = LawyerFresherProfile.builder()
                    .name(name)
                    .email(email)
                    .phone(null)
                    .passwordHash(null)
                    .provider(com.askvocate.backend.entity.AuthProvider.GOOGLE)
                    .createdAt(java.time.Instant.now().toString())
                    .build();
            newUserProfile = lawyerFresherService.saveGoogleLawyerFresher(profile);
        } else if ("LAWYER_EXPERIENCED".equals(finalRole)) {
            LawyerExperiencedProfile profile = LawyerExperiencedProfile.builder()
                    .name(name)
                    .email(email)
                    .phone(null)
                    .passwordHash(null)
                    .provider(com.askvocate.backend.entity.AuthProvider.GOOGLE)
                    .createdAt(java.time.Instant.now().toString())
                    .build();
            newUserProfile = lawyerExperiencedService.saveGoogleLawyerExperienced(profile);
        } else {
            ClientProfile profile = ClientProfile.builder()
                    .name(name)
                    .email(email)
                    .phone(null)
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
        result.put("profileImageUrl", payload.get("picture"));
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
        String input = dto.getEmail().trim().toLowerCase();
        String password = dto.getPassword();

        // 1. Check Client
        Optional<ClientProfile> clientOpt = clientService.findByEmail(input);
        if (clientOpt.isPresent()) {
            ClientProfile client = clientOpt.get();
            if (client.getEmail() == null) {
                client.setEmail(input);
                clientService.saveGoogleClient(client);
            }
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
        Optional<LawyerFresherProfile> fresherOpt = lawyerFresherService.findByEmail(input);
        if (fresherOpt.isPresent()) {
            LawyerFresherProfile fresher = fresherOpt.get();
            if (fresher.getEmail() == null) {
                fresher.setEmail(input);
                lawyerFresherService.saveGoogleLawyerFresher(fresher);
            }
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
        Optional<LawyerExperiencedProfile> expOpt = lawyerExperiencedService.findByEmail(input);
        if (expOpt.isPresent()) {
            LawyerExperiencedProfile exp = expOpt.get();
            if (exp.getEmail() == null) {
                exp.setEmail(input);
                lawyerExperiencedService.saveGoogleLawyerExperienced(exp);
            }
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

