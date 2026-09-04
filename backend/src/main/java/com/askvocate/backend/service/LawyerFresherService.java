package com.askvocate.backend.service;

import com.askvocate.backend.dto.LawyerFresherSignup;
import com.askvocate.backend.entity.Verification_Status;
import com.askvocate.backend.model.LawyerFresherProfile;
import com.askvocate.backend.repository.LawyerFresherProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LawyerFresherService {

    @Autowired
    private LawyerFresherProfileRepository lawyerFresherProfileRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Registers a new fresher lawyer.
     * Validates password match and email/phone uniqueness before saving.
     * Profile starts with verificationStatus = PENDING.
     */
    public LawyerFresherProfile register(LawyerFresherSignup dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (lawyerFresherProfileRepository.existsByEmailOrPhone(dto.getEmailOrPhone())) {
            throw new IllegalArgumentException("Account already exists with this email or phone");
        }

        LawyerFresherProfile profile = LawyerFresherProfile.builder()
                .name(dto.getName())
                .emailOrPhone(dto.getEmailOrPhone())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .university(dto.getUniversity() != null ? dto.getUniversity() : "")
                .graduationYear(dto.getGraduationYear() != null ? dto.getGraduationYear() : 0)
                .specialization(dto.getSpecialization() != null ? dto.getSpecialization() : "")
                .createdAt(java.time.Instant.now().toString())
                .build();   // verificationStatus defaults to PENDING via @Builder.Default

        return lawyerFresherProfileRepository.save(profile);
    }

    /** Fetch a fresher lawyer by their email or phone number. */
    public Optional<LawyerFresherProfile> findByEmailOrPhone(String emailOrPhone) {
        return lawyerFresherProfileRepository.findByEmailOrPhone(emailOrPhone);
    }

    /** Fetch a fresher lawyer by their MongoDB ID. */
    public Optional<LawyerFresherProfile> findById(String id) {
        return lawyerFresherProfileRepository.findById(id);
    }

    /** Returns all fresher lawyers with a given verification status (e.g. PENDING). */
    public List<LawyerFresherProfile> findByVerifStatus(Verification_Status status) {
        return lawyerFresherProfileRepository.findAll()
                .stream()
                .filter(p -> p.getVerificationStatus() == status)
                .toList();
    }

    /** Admin: approve a fresher lawyer's profile. */
    public LawyerFresherProfile approve(String id) {
        LawyerFresherProfile profile = lawyerFresherProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lawyer fresher profile not found: " + id));

        profile.setVerificationStatus(Verification_Status.VERIFIED);
        profile.setVerifiedAt(System.currentTimeMillis());
        profile.setRejectionReason(null);
        return lawyerFresherProfileRepository.save(profile);
    }

    /** Admin: reject a fresher lawyer's profile with a reason. */
    public LawyerFresherProfile reject(String id, String reason) {
        LawyerFresherProfile profile = lawyerFresherProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lawyer fresher profile not found: " + id));

        profile.setVerificationStatus(Verification_Status.REJECTED);
        profile.setRejectionReason(reason);
        return lawyerFresherProfileRepository.save(profile);
    }
}
