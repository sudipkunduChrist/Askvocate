package com.askvocate.backend.service;

import com.askvocate.backend.dto.LoginRequest;
import com.askvocate.backend.model.ClientProfile;
import com.askvocate.backend.model.LawyerExperiencedProfile;
import com.askvocate.backend.model.LawyerFresherProfile;
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
