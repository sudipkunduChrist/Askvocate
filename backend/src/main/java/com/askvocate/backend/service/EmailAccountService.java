package com.askvocate.backend.service;

import com.askvocate.backend.model.ClientProfile;
import com.askvocate.backend.model.LawyerExperiencedProfile;
import com.askvocate.backend.model.LawyerFresherProfile;
import com.askvocate.backend.repository.ClientProfileRepository;
import com.askvocate.backend.repository.LawyerExperiencedProfileRepository;
import com.askvocate.backend.repository.LawyerFresherProfileRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Enforces one account per email across the client, fresher and experienced
 * lawyer collections. Each collection has its own unique email index, so
 * cross-role uniqueness must be checked here.
 */
@Service
public class EmailAccountService {
    public record Account(String role, Object user) {}

    private final ClientProfileRepository clients;
    private final LawyerFresherProfileRepository freshers;
    private final LawyerExperiencedProfileRepository experienced;

    public EmailAccountService(ClientProfileRepository clients,
            LawyerFresherProfileRepository freshers,
            LawyerExperiencedProfileRepository experienced) {
        this.clients = clients;
        this.freshers = freshers;
        this.experienced = experienced;
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public Optional<Account> findAccount(String email) {
        String value = normalize(email);
        if (value == null || value.isBlank()) return Optional.empty();
        String regex = "^" + Pattern.quote(value) + "$";

        Optional<ClientProfile> client = clients.findByEmail(value);
        if (client.isEmpty()) client = clients.findByEmailRegex(regex);
        if (client.isPresent()) return Optional.of(new Account("CLIENT", client.get()));

        Optional<LawyerFresherProfile> fresher = freshers.findByEmail(value);
        if (fresher.isEmpty()) fresher = freshers.findByEmailRegex(regex);
        if (fresher.isPresent()) return Optional.of(new Account("LAWYER_FRESHER", fresher.get()));

        Optional<LawyerExperiencedProfile> lawyer = experienced.findByEmail(value);
        if (lawyer.isEmpty()) lawyer = experienced.findByEmailRegex(regex);
        if (lawyer.isPresent()) return Optional.of(new Account("LAWYER_EXPERIENCED", lawyer.get()));

        return Optional.empty();
    }

    public Optional<String> roleForEmail(String email) {
        return findAccount(email).map(Account::role);
    }

    /** Rejects registration when the email already belongs to any role. */
    public void requireAvailable(String email) {
        findAccount(email).ifPresent(account -> {
            throw new IllegalArgumentException(
                    "An account with this email already exists. Sign in with that account, "
                    + "or use a different email.");
        });
    }

    /** Allows the current account to keep its email while blocking another account's. */
    public void requireEmailChangeAvailable(String email, String currentUserId) {
        findAccount(email).ifPresent(account -> {
            String ownerId = idOf(account.user());
            if (currentUserId == null || !currentUserId.equals(ownerId)) {
                throw new IllegalArgumentException(
                        "An account with this email already exists. Sign in with that account, "
                        + "or use a different email.");
            }
        });
    }

    public String idOf(Object user) {
        if (user instanceof ClientProfile profile) return profile.getId();
        if (user instanceof LawyerFresherProfile profile) return profile.getId();
        if (user instanceof LawyerExperiencedProfile profile) return profile.getId();
        return null;
    }
}
