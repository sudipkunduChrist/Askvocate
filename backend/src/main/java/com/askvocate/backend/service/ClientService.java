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
        if (clientProfileRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Account already exists with this email or phone");
        }

        ClientProfile profile = ClientProfile.builder()
                .name(dto.getName())
                .email(dto.getEmail().trim().toLowerCase())
                .phone(null)
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .provider(dto.getProvider() != null ? dto.getProvider() : com.askvocate.backend.entity.AuthProvider.LOCAL)
                .createdAt(java.time.Instant.now().toString())
                .build();

        return clientProfileRepository.save(profile);
    }

    /** Fetch a client by their email or phone number. */
    public Optional<ClientProfile> findByEmail(String email) {
        return clientProfileRepository.findByEmail(email);
    }

    /** Fetch a client by their MongoDB ID. */
    public Optional<ClientProfile> findById(String id) {
        return clientProfileRepository.findById(id);
    }

    /**
     * Saves a client created via Google Sign-In (no local password).
     * Assumes uniqueness was checked by the caller right before construction.
     */
    public ClientProfile saveGoogleClient(ClientProfile profile) {
        return clientProfileRepository.save(profile);
    }

    /**
     * Updates an existing client profile.
     */
    public ClientProfile updateClientProfile(String id, com.askvocate.backend.dto.ProfileUpdateRequest dto) {
        ClientProfile profile = clientProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        if (dto.getName() != null) profile.setName(dto.getName());
        if (dto.getEmail() != null) profile.setEmail(dto.getEmail());
        if (dto.getPhone() != null) profile.setPhone(dto.getPhone());
        if (dto.getAddress() != null) profile.setAddress(dto.getAddress());

        return clientProfileRepository.save(profile);
    }
}
