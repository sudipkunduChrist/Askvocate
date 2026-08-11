package com.askvocate.backend.service;

import com.askvocate.backend.dto.ClientSignUp;
import com.askvocate.backend.model.ClientProfile;
import com.askvocate.backend.repository.ClientProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClientService {

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Registers a new client.
     * Validates password match and email/phone uniqueness before saving.
     */
    public ClientProfile register(ClientSignUp dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (clientProfileRepository.existsByEmailOrPhone(dto.getEmailOrPhone())) {
            throw new IllegalArgumentException("Account already exists with this email or phone");
        }

        ClientProfile profile = ClientProfile.builder()
                .name(dto.getName())
                .emailOrPhone(dto.getEmailOrPhone())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .build();

        return clientProfileRepository.save(profile);
    }

    /** Fetch a client by their email or phone number. */
    public Optional<ClientProfile> findByEmailOrPhone(String emailOrPhone) {
        return clientProfileRepository.findByEmailOrPhone(emailOrPhone);
    }

    /** Fetch a client by their MongoDB ID. */
    public Optional<ClientProfile> findById(String id) {
        return clientProfileRepository.findById(id);
    }
}
